package io.vurli;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Two chains:
 * <ul>
 * <li>{@code /api/**}: the JSON API the Next.js panel calls. Session based; a few account
 * endpoints are open, everything else needs a signed-in user.</li>
 * <li>everything else: short links and the health check are public, the rest is denied.
 * The admin panel (M3) adds its own chain.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
				new HttpSessionSecurityContextRepository());
	}

	@Bean
	@Order(1)
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository)
			throws Exception {
		http.securityMatcher("/api/**")
			.authorizeHttpRequests((requests) -> requests
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
						"/api/v1/auth/verify-email", "/api/v1/auth/password-reset",
						"/api/v1/auth/password-reset/confirm")
				.permitAll()
				.anyRequest()
				.authenticated())
			// Replaced by ClientHeaderFilter and the SameSite session cookie.
			.csrf(AbstractHttpConfigurer::disable)
			.addFilterBefore(new ClientHeaderFilter(), AuthorizationFilter.class)
			.securityContext((context) -> context.securityContextRepository(contextRepository))
			// An API answers 401; it never redirects to a login page or remembers the request.
			.exceptionHandling((errors) -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.requestCache((cache) -> cache.requestCache(new NullRequestCache()));
		return http.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((requests) -> requests
			// Error dispatches must render the real status, not a 403 from denyAll below.
			.dispatcherTypeMatchers(DispatcherType.ERROR)
			.permitAll()
			.requestMatchers(HttpMethod.GET, "/actuator/health")
			.permitAll()
			// Single path segment only: /{code}
			.requestMatchers(HttpMethod.GET, "/*")
			.permitAll()
			.requestMatchers(HttpMethod.HEAD, "/*")
			.permitAll()
			.anyRequest()
			.denyAll());
		return http.build();
	}

}
