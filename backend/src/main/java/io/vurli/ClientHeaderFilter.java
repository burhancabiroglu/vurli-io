package io.vurli;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF defence for the JSON API: state-changing requests must carry
 * {@value #HEADER}. A cross-site form or link cannot add custom headers, and a cross-site
 * script cannot either without a CORS preflight, which the API never allows. Together
 * with the {@code SameSite=Lax} session cookie this replaces token-based CSRF, which
 * fits poorly with a BFF that is the API's only client.
 * <p>
 * Not a Spring bean on purpose: it is added to the API security chain only.
 */
public class ClientHeaderFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Vurli-Client";

	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!SAFE_METHODS.contains(request.getMethod()) && request.getHeader(HEADER) == null) {
			response.setStatus(HttpStatus.FORBIDDEN.value());
			return;
		}
		chain.doFilter(request, response);
	}

}
