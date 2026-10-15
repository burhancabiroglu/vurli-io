package io.vurli;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring serves three audiences on different hosts (K20, K43): visitors of short links on
 * the public domain, administrators on the admin domain, and the Next.js panel and health
 * checks on the internal network. A request whose {@code Host} does not belong to its
 * audience gets a bare 404 before sessions or security run.
 * <p>
 * {@code /admin} and everything below it is admin; other single-segment paths
 * ({@code /} and {@code /{code}}) are public; everything else is internal. Classifying unknown paths as internal means a path trick such as
 * {@code //api/v1/links} cannot reach the API through the public domain.
 */
public class HostFilter extends OncePerRequestFilter {

	private final Set<String> publicHosts;

	private final Set<String> internalHosts;

	private final Set<String> adminHosts;

	public HostFilter(Collection<String> publicHosts, Collection<String> internalHosts,
			Collection<String> adminHosts) {
		this.publicHosts = normalize(publicHosts);
		this.internalHosts = normalize(internalHosts);
		this.adminHosts = normalize(adminHosts);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String path = request.getRequestURI();
		Set<String> allowed = isAdminPath(path) ? this.adminHosts
				: isPublicPath(path) ? this.publicHosts : this.internalHosts;
		String host = request.getServerName();
		if (host == null || !allowed.contains(host.toLowerCase(Locale.ROOT))) {
			response.setStatus(HttpStatus.NOT_FOUND.value());
			response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
			return;
		}
		chain.doFilter(request, response);
	}

	private static boolean isAdminPath(String path) {
		return path.equals("/admin") || path.startsWith("/admin/");
	}

	private static boolean isPublicPath(String path) {
		return path.startsWith("/") && path.indexOf('/', 1) == -1;
	}

	private static Set<String> normalize(Collection<String> hosts) {
		return hosts.stream().map((host) -> host.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
	}

}
