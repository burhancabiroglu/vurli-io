package io.vurli.admin.web;

import java.io.Serial;
import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.vurli.abuse.AuditLog;
import io.vurli.abuse.ClientAddresses;
import io.vurli.abuse.RateLimit;
import io.vurli.abuse.RateLimitExceededException;
import io.vurli.abuse.RateLimiter;
import io.vurli.account.InvalidCodeException;
import io.vurli.account.TotpEnrollment;
import io.vurli.account.TwoFactorAuthentication;
import io.vurli.account.VurliUser;
import io.vurli.admin.AdminSecurity;
import io.vurli.qr.QrCodeRenderer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Administrator sign-in in two steps. The password step only remembers who is halfway
 * in (for five minutes); the session becomes signed in after the second factor, with a
 * new session id each time. An account that is not an administrator gets the same answer
 * as a wrong password.
 */
@Controller
@RequestMapping("/admin/login")
class AdminLoginController {

	static final String PENDING = AdminLoginController.class.getName() + ".pending";

	private static final Duration PENDING_TIME = Duration.ofMinutes(5);

	/** Halfway signed in: the password was right, the second factor is still missing. */
	record Pending(VurliUser user, Instant expiresAt) implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

	}

	private final AuthenticationManager authenticationManager;

	private final TwoFactorAuthentication twoFactor;

	private final RateLimiter rateLimiter;

	private final ClientAddresses clientAddresses;

	private final AuditLog audit;

	private final SecurityContextRepository contextRepository;

	private final Clock clock;

	private final Duration sessionTimeout;

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	AdminLoginController(AuthenticationManager authenticationManager, TwoFactorAuthentication twoFactor,
			RateLimiter rateLimiter, ClientAddresses clientAddresses, AuditLog audit,
			SecurityContextRepository contextRepository, Clock clock,
			@Value("${vurli.admin.session-timeout:1h}") Duration sessionTimeout) {
		this.authenticationManager = authenticationManager;
		this.twoFactor = twoFactor;
		this.rateLimiter = rateLimiter;
		this.clientAddresses = clientAddresses;
		this.audit = audit;
		this.contextRepository = contextRepository;
		this.clock = clock;
		this.sessionTimeout = sessionTimeout;
	}

	@GetMapping
	String loginForm() {
		return "admin/login";
	}

	@PostMapping
	String login(@RequestParam String email, @RequestParam String password, HttpServletRequest request,
			Model model) {
		String account = email.trim().toLowerCase(Locale.ROOT);
		try {
			this.rateLimiter.consume(RateLimit.LOGIN_PER_IP, this.clientAddresses.rateLimitKey(request));
			this.rateLimiter.check(RateLimit.LOGIN_FAILURES_PER_ACCOUNT, account);
		}
		catch (RateLimitExceededException ex) {
			return error(model, "admin/login", "admin.error.too-many");
		}

		VurliUser user;
		try {
			user = (VurliUser) this.authenticationManager
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password))
				.getPrincipal();
		}
		catch (AuthenticationException ex) {
			this.rateLimiter.record(RateLimit.LOGIN_FAILURES_PER_ACCOUNT, account);
			return error(model, "admin/login", "admin.error.credentials");
		}
		if (!this.twoFactor.isAdmin(user.userId())) {
			this.rateLimiter.record(RateLimit.LOGIN_FAILURES_PER_ACCOUNT, account);
			return error(model, "admin/login", "admin.error.credentials");
		}

		request.getSession(true);
		request.changeSessionId();
		request.getSession().setAttribute(PENDING, new Pending(user, this.clock.instant().plus(PENDING_TIME)));
		return this.twoFactor.isEnrolled(user.userId()) ? "redirect:/admin/login/code" : "redirect:/admin/login/setup";
	}

	@GetMapping("/code")
	String codeForm(HttpServletRequest request) {
		return pending(request).isPresent() ? "admin/code" : "redirect:/admin/login";
	}

	/** A TOTP code or a backup code. */
	@PostMapping("/code")
	String code(@RequestParam String code, HttpServletRequest request, HttpServletResponse response, Model model) {
		Optional<Pending> pending = pending(request);
		if (pending.isEmpty()) {
			return "redirect:/admin/login";
		}
		VurliUser user = pending.get().user();
		String key = user.userId().toString();
		try {
			this.rateLimiter.check(RateLimit.TOTP_FAILURES_PER_USER, key);
		}
		catch (RateLimitExceededException ex) {
			return error(model, "admin/code", "admin.error.too-many");
		}
		Optional<TwoFactorAuthentication.Method> method = this.twoFactor.verify(user.userId(), code);
		if (method.isEmpty()) {
			this.rateLimiter.record(RateLimit.TOTP_FAILURES_PER_USER, key);
			this.audit.record(user.userId(), "ADMIN_CODE_REJECTED", "USER", key, Map.of());
			return error(model, "admin/code", "admin.error.code");
		}
		signIn(user, method.get().name(), request, response);
		return "redirect:/admin";
	}

	@GetMapping("/setup")
	String setup(HttpServletRequest request, Model model) {
		Optional<Pending> pending = pending(request);
		if (pending.isEmpty()) {
			return "redirect:/admin/login";
		}
		if (this.twoFactor.isEnrolled(pending.get().user().userId())) {
			return "redirect:/admin/login/code";
		}
		return setupPage(pending.get().user(), model);
	}

	/**
	 * The first code confirms the app; the backup codes are rendered directly, never
	 * stored in the session or sent in a redirect.
	 */
	@PostMapping("/setup")
	String confirmSetup(@RequestParam String code, HttpServletRequest request, HttpServletResponse response,
			Model model) {
		Optional<Pending> pending = pending(request);
		if (pending.isEmpty()) {
			return "redirect:/admin/login";
		}
		VurliUser user = pending.get().user();
		String key = user.userId().toString();
		try {
			this.rateLimiter.check(RateLimit.TOTP_FAILURES_PER_USER, key);
		}
		catch (RateLimitExceededException ex) {
			model.addAttribute("error", "admin.error.too-many");
			return setupPage(user, model);
		}
		List<String> backupCodes;
		try {
			backupCodes = this.twoFactor.confirmEnrollment(user.userId(), code);
		}
		catch (InvalidCodeException ex) {
			this.rateLimiter.record(RateLimit.TOTP_FAILURES_PER_USER, key);
			model.addAttribute("error", "admin.error.code");
			return setupPage(user, model);
		}
		signIn(user, "ENROLLMENT", request, response);
		model.addAttribute("backupCodes", backupCodes);
		return "admin/backup-codes";
	}

	private String setupPage(VurliUser user, Model model) {
		TotpEnrollment enrollment = this.twoFactor.enrollment(user.userId(), user.getUsername());
		model.addAttribute("secret", enrollment.secret());
		// An inline image: the secret never appears in a URL or a server log.
		model.addAttribute("qr",
				"data:image/png;base64," + Base64.getEncoder().encodeToString(QrCodeRenderer.png(enrollment.uri())));
		return "admin/setup";
	}

	private void signIn(VurliUser user, String method, HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession();
		session.removeAttribute(PENDING);
		request.changeSessionId();
		session.setMaxInactiveInterval((int) this.sessionTimeout.toSeconds());
		SecurityContext context = this.contextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null,
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority(AdminSecurity.SECOND_FACTOR))));
		this.contextHolder.setContext(context);
		this.contextRepository.saveContext(context, request, response);
		this.audit.record(user.userId(), "ADMIN_LOGIN", "USER", user.userId().toString(), Map.of("method", method));
	}

	private Optional<Pending> pending(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(PENDING) instanceof Pending pending)) {
			return Optional.empty();
		}
		if (!this.clock.instant().isBefore(pending.expiresAt())) {
			session.removeAttribute(PENDING);
			return Optional.empty();
		}
		return Optional.of(pending);
	}

	private static String error(Model model, String view, String messageKey) {
		model.addAttribute("error", messageKey);
		return view;
	}

}
