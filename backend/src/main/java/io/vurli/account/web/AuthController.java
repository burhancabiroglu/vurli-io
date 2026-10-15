package io.vurli.account.web;

import java.util.Locale;

import io.vurli.account.AccountManagement;
import io.vurli.account.AccountView;
import io.vurli.account.VurliUser;
import io.vurli.ratelimit.ClientAddresses;
import io.vurli.ratelimit.RateLimit;
import io.vurli.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account endpoints for the Next.js panel. Responses never reveal whether an address has
 * an account: registration and reset requests always answer 202.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private static final String SESSION_COOKIE = "VURLI_SESSION";

	private final AccountManagement accounts;

	private final AuthenticationManager authenticationManager;

	private final SecurityContextRepository contextRepository;

	private final RateLimiter rateLimiter;

	private final ClientAddresses clientAddresses;

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	AuthController(AccountManagement accounts, AuthenticationManager authenticationManager,
			SecurityContextRepository contextRepository, RateLimiter rateLimiter, ClientAddresses clientAddresses) {
		this.accounts = accounts;
		this.authenticationManager = authenticationManager;
		this.contextRepository = contextRepository;
		this.rateLimiter = rateLimiter;
		this.clientAddresses = clientAddresses;
	}

	/**
	 * Requests that send an email share one limit per address, so the panel cannot be used
	 * to flood someone's inbox. Counted before anything else, whatever the outcome.
	 */
	private void limitEmails(HttpServletRequest request) {
		this.rateLimiter.consume(RateLimit.EMAILS_PER_IP, this.clientAddresses.rateLimitKey(request));
	}

	@PostMapping("/register")
	ResponseEntity<Void> register(@RequestBody Register request, Locale locale, HttpServletRequest httpRequest) {
		limitEmails(httpRequest);
		this.accounts.register(request.email(), request.password(), locale);
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/verify-email")
	ResponseEntity<Void> verifyEmail(@RequestBody Token request) {
		this.accounts.verifyEmail(request.token());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/verification-email")
	ResponseEntity<Void> resendVerification(@AuthenticationPrincipal VurliUser user, HttpServletRequest httpRequest) {
		limitEmails(httpRequest);
		this.accounts.resendVerification(user.userId());
		return ResponseEntity.accepted().build();
	}

	/**
	 * Two limits: attempts per address, and failures per email address from anywhere
	 * (against password guessing spread over many addresses). The second is checked
	 * before the password, so a locked address answers 429 even with the right password.
	 * It applies to unknown addresses too and reveals nothing. Its cost: someone can lock
	 * a user out for a while by failing on purpose (accepted in K42).
	 */
	@PostMapping("/login")
	AccountView login(@RequestBody Login request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		this.rateLimiter.consume(RateLimit.LOGIN_PER_IP, this.clientAddresses.rateLimitKey(httpRequest));
		String account = (request.email() != null) ? request.email().trim().toLowerCase(Locale.ROOT) : "";
		this.rateLimiter.check(RateLimit.LOGIN_FAILURES_PER_ACCOUNT, account);
		Authentication authentication;
		try {
			authentication = this.authenticationManager
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
		}
		catch (AuthenticationException ex) {
			this.rateLimiter.record(RateLimit.LOGIN_FAILURES_PER_ACCOUNT, account);
			throw ex;
		}
		// A new session id on login: an id planted before login is worthless afterwards.
		httpRequest.getSession(true);
		httpRequest.changeSessionId();
		SecurityContext context = this.contextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		this.contextHolder.setContext(context);
		this.contextRepository.saveContext(context, httpRequest, httpResponse);
		return this.accounts.view((VurliUser) authentication.getPrincipal());
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request,
			HttpServletResponse response) {
		new SecurityContextLogoutHandler().logout(request, response, authentication);
		new CookieClearingLogoutHandler(SESSION_COOKIE).logout(request, response, authentication);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	AccountView me(@AuthenticationPrincipal VurliUser user) {
		return this.accounts.view(user);
	}

	@PostMapping("/password-reset")
	ResponseEntity<Void> requestPasswordReset(@RequestBody Email request, HttpServletRequest httpRequest) {
		limitEmails(httpRequest);
		this.accounts.requestPasswordReset(request.email());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/password-reset/confirm")
	ResponseEntity<Void> resetPassword(@RequestBody ResetPassword request) {
		this.accounts.resetPassword(request.token(), request.password());
		return ResponseEntity.noContent().build();
	}

	record Register(String email, String password) {
	}

	record Login(String email, String password) {
	}

	record Token(String token) {
	}

	record Email(String email) {
	}

	record ResetPassword(String token, String password) {
	}

}
