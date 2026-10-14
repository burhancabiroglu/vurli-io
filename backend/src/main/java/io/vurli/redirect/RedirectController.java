package io.vurli.redirect;

import io.vurli.link.LinkResolution;
import io.vurli.link.LinkResolver;
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

	RedirectController(LinkResolver resolver) {
		this.resolver = resolver;
	}

	@GetMapping("/{code}")
	ModelAndView redirect(@PathVariable String code, HttpServletResponse response) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
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

	/** Without the database we cannot tell whether a link is still allowed, so we never guess. */
	@ExceptionHandler(DataAccessException.class)
	ModelAndView unavailable(HttpServletResponse response) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
		return new ModelAndView("link/unavailable", HttpStatus.SERVICE_UNAVAILABLE);
	}

}
