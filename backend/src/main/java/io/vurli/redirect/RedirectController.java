package io.vurli.redirect;

import io.vurli.abuse.ClientAddresses;
import io.vurli.abuse.RateLimit;
import io.vurli.abuse.RateLimitExceededException;
import io.vurli.abuse.RateLimiter;
import io.vurli.link.LinkResolution;
import io.vurli.link.LinkResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

/**
 * Sends visitors of {@code /{code}} to the link's target. {@code HEAD} is served by the
 * same mapping without a body.
 */
@Controller
class RedirectController {

	/** Targets can change, so neither browsers nor proxies may remember any answer. */
	private static final String NO_STORE = "no-store";

	private final LinkResolver resolver;

	private final RateLimiter rateLimiter;

	private final ClientAddresses clientAddresses;

	RedirectController(LinkResolver resolver, RateLimiter rateLimiter, ClientAddresses clientAddresses) {
		this.resolver = resolver;
		this.rateLimiter = rateLimiter;
		this.clientAddresses = clientAddresses;
	}

	@GetMapping("/{code}")
	ModelAndView redirect(@PathVariable String code, HttpServletRequest request, HttpServletResponse response) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
		// Generous for people, but stops one address from scanning codes or hammering a link.
		this.rateLimiter.consume(RateLimit.REDIRECTS_PER_IP, this.clientAddresses.rateLimitKey(request));
		return switch (this.resolver.resolve(code)) {
			case LinkResolution.Redirect redirect -> {
				// 302, not 301: browsers cache 301 permanently and would ignore a later target change.
				// The visitor's query string is deliberately not forwarded.
				response.setStatus(HttpStatus.FOUND.value());
				response.setHeader(HttpHeaders.LOCATION, redirect.targetUrl());
				yield null;
			}
			case LinkResolution.Gone gone -> new ModelAndView("link/gone", HttpStatus.GONE);
			case LinkResolution.NotFound notFound -> new ModelAndView("link/not-found", HttpStatus.NOT_FOUND);
		};
	}

	@ExceptionHandler(RateLimitExceededException.class)
	ModelAndView tooManyRequests(RateLimitExceededException ex, HttpServletResponse response) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
		response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()));
		return new ModelAndView("link/too-many-requests", HttpStatus.TOO_MANY_REQUESTS);
	}

	/** Without the database we cannot tell whether a link is still allowed, so we never guess. */
	@ExceptionHandler(DataAccessException.class)
	ModelAndView unavailable(HttpServletResponse response) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
		return new ModelAndView("link/unavailable", HttpStatus.SERVICE_UNAVAILABLE);
	}

}
