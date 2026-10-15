package io.vurli.link.web;

import io.vurli.abuse.RateLimitExceededException;
import io.vurli.link.AliasTakenException;
import io.vurli.link.InvalidAliasException;
import io.vurli.link.InvalidExpiryException;
import io.vurli.link.InvalidTargetUrlException;
import io.vurli.link.LinkBlockedException;
import io.vurli.link.LinkNotFoundException;
import io.vurli.link.LinkQuotaExceededException;
import io.vurli.link.LinkVersionConflictException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errors as RFC 9457 problem details with a stable {@code code} the panel can translate.
 */
@RestControllerAdvice(assignableTypes = LinkApiController.class)
class LinkApiExceptionHandler {

	@ExceptionHandler(LinkApiController.EmailNotVerifiedException.class)
	ProblemDetail emailNotVerified() {
		return problem(HttpStatus.FORBIDDEN, "email_not_verified", null);
	}

	@ExceptionHandler(RateLimitExceededException.class)
	ResponseEntity<ProblemDetail> rateLimited(RateLimitExceededException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
			.body(problem(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", null));
	}

	@ExceptionHandler(LinkQuotaExceededException.class)
	ProblemDetail quotaExceeded(LinkQuotaExceededException ex) {
		ProblemDetail problem = problem(HttpStatus.TOO_MANY_REQUESTS, "link_quota_exceeded", null);
		problem.setProperty("quota", ex.quota());
		return problem;
	}

	@ExceptionHandler(LinkApiController.InvalidQrFormatException.class)
	ProblemDetail invalidQrFormat() {
		return problem(HttpStatus.BAD_REQUEST, "invalid_format", null);
	}

	@ExceptionHandler(InvalidTargetUrlException.class)
	ProblemDetail invalidTarget(InvalidTargetUrlException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_target", ex.reason().name());
	}

	@ExceptionHandler(InvalidAliasException.class)
	ProblemDetail invalidAlias(InvalidAliasException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_alias", ex.reason().name());
	}

	@ExceptionHandler(InvalidExpiryException.class)
	ProblemDetail invalidExpiry() {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_expiry", null);
	}

	@ExceptionHandler(AliasTakenException.class)
	ProblemDetail aliasTaken() {
		return problem(HttpStatus.CONFLICT, "alias_taken", null);
	}

	@ExceptionHandler(LinkVersionConflictException.class)
	ProblemDetail versionConflict() {
		return problem(HttpStatus.CONFLICT, "version_conflict", null);
	}

	@ExceptionHandler(LinkBlockedException.class)
	ProblemDetail blocked() {
		return problem(HttpStatus.CONFLICT, "link_blocked", null);
	}

	@ExceptionHandler(LinkNotFoundException.class)
	ProblemDetail notFound() {
		return problem(HttpStatus.NOT_FOUND, "link_not_found", null);
	}

	private static ProblemDetail problem(HttpStatus status, String code, String reason) {
		ProblemDetail problem = ProblemDetail.forStatus(status);
		problem.setProperty("code", code);
		if (reason != null) {
			problem.setProperty("reason", reason);
		}
		return problem;
	}

}
