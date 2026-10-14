package io.vurli.account.web;

import java.util.Locale;

import io.vurli.account.AccountManagement;
import io.vurli.account.AccountView;
import io.vurli.account.VurliUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	AuthController(AccountManagement accounts, AuthenticationManager authenticationManager,
			SecurityContextRepository contextRepository) {
		this.accounts = accounts;
		this.authenticationManager = authenticationManager;
		this.contextRepository = contextRepository;
	}

	@PostMapping("/register")
	ResponseEntity<Void> register(@RequestBody Register request, Locale locale) {
		this.accounts.register(request.email(), request.password(), locale);
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/verify-email")
	ResponseEntity<Void> verifyEmail(@RequestBody Token request) {
		this.accounts.verifyEmail(request.token());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/verification-email")
	ResponseEntity<Void> resendVerification(@AuthenticationPrincipal VurliUser user) {
		this.accounts.resendVerification(user.userId());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/login")
	AccountView login(@RequestBody Login request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		Authentication authentication = this.authenticationManager
			.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
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
	ResponseEntity<Void> requestPasswordReset(@RequestBody Email request) {
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
