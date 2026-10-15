package io.vurli.account.web;

import io.vurli.abuse.RateLimitExceededException;
import io.vurli.account.InvalidEmailException;
import io.vurli.account.InvalidTokenException;
import io.vurli.account.TooManyEmailsException;
import io.vurli.account.WeakPasswordException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errors as RFC 9457 problem details with a stable {@code code} the panel can translate.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
class AccountApiExceptionHandler {

	/** Wrong password and unknown address are deliberately the same answer. */
	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail invalidCredentials() {
		return problem(HttpStatus.UNAUTHORIZED, "invalid_credentials", null);
	}

	@ExceptionHandler(RateLimitExceededException.class)
	ResponseEntity<ProblemDetail> rateLimited(RateLimitExceededException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
			.body(problem(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", null));
	}

	@ExceptionHandler(InvalidEmailException.class)
	ProblemDetail invalidEmail() {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_email", null);
	}

	@ExceptionHandler(WeakPasswordException.class)
	ProblemDetail weakPassword(WeakPasswordException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "weak_password", ex.reason().name());
	}

	@ExceptionHandler(InvalidTokenException.class)
	ProblemDetail invalidToken() {
		return problem(HttpStatus.BAD_REQUEST, "invalid_token", null);
	}

	@ExceptionHandler(TooManyEmailsException.class)
	ResponseEntity<ProblemDetail> tooManyEmails(TooManyEmailsException ex) {
		long seconds = Math.max(1, ex.retryAfter().toSeconds());
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
			.body(problem(HttpStatus.TOO_MANY_REQUESTS, "too_many_emails", null));
	}

	static ProblemDetail problem(HttpStatus status, String code, String reason) {
		ProblemDetail problem = ProblemDetail.forStatus(status);
		problem.setProperty("code", code);
		if (reason != null) {
			problem.setProperty("reason", reason);
		}
		return problem;
	}

}
