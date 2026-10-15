package io.vurli;

import java.util.LinkedHashMap;
import java.util.Map;

import io.vurli.admin.AdminSecurity;
import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.DelegatingAccessDeniedHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Three chains:
 * <ul>
 * <li>{@code /admin/**}: the admin panel on its own host (K43). Form pages with
 * Spring Security's CSRF tokens, a strict CSP, and access only after the second factor
 * (K47).</li>
 * <li>{@code /api/**}: the JSON API the Next.js panel calls. Session based; a few account
 * endpoints are open, everything else needs a signed-in user.</li>
 * <li>everything else: short links and the health check are public, the rest is denied.
 * </li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
				new HttpSessionSecurityContextRepository());
	}

	/**
	 * The admin panel. Every page except sign-in needs {@link AdminSecurity#SECOND_FACTOR};
	 * anything less, including a password-only session with ROLE_ADMIN, is sent to the
	 * sign-in page. Pages load only our own stylesheet and inline QR images.
	 */
	@Bean
	@Order(0)
	SecurityFilterChain adminSecurityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository)
			throws Exception {
		AuthenticationEntryPoint toLogin = new LoginUrlAuthenticationEntryPoint("/admin/login");
		http.securityMatcher("/admin", "/admin/**")
			.authorizeHttpRequests((requests) -> requests
				.requestMatchers("/admin/login", "/admin/login/**", "/admin/assets/**")
				.permitAll()
				.anyRequest()
				.hasAuthority(AdminSecurity.SECOND_FACTOR))
			.securityContext((context) -> context.securityContextRepository(contextRepository))
			// A missing or wrong CSRF token is a plain 403; any other denial means "sign in".
			.exceptionHandling((errors) -> errors.authenticationEntryPoint(toLogin)
				.accessDeniedHandler(new DelegatingAccessDeniedHandler(
						new LinkedHashMap<>(Map.of(CsrfException.class, new AccessDeniedHandlerImpl())),
						(request, response, ex) -> toLogin.commence(request, response, null))))
			.requestCache((cache) -> cache.requestCache(new NullRequestCache()))
			.logout((logout) -> logout.logoutUrl("/admin/logout")
				.logoutSuccessUrl("/admin/login?logout")
				.deleteCookies(SessionCookieConfiguration.ADMIN_COOKIE_NAME))
			.headers((headers) -> headers
				.contentSecurityPolicy((csp) -> csp.policyDirectives("default-src 'none'; style-src 'self'; "
						+ "img-src 'self' data:; form-action 'self'; frame-ancestors 'none'; base-uri 'none'"))
				.referrerPolicy((referrer) -> referrer.policy(ReferrerPolicy.NO_REFERRER)));
		return http.build();
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
	SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
			@Value("${vurli.session.secure-cookie:true}") boolean secureCookie) throws Exception {
		http.authorizeHttpRequests((requests) -> requests
			// Error dispatches must render the real status, not a 403 from denyAll below.
			.dispatcherTypeMatchers(DispatcherType.ERROR)
			.permitAll()
			.requestMatchers(HttpMethod.GET, "/actuator/health")
			.permitAll()
			// The public report form (K51); the GET is covered by /* below.
			.requestMatchers(HttpMethod.POST, "/abuse")
			.permitAll()
			// Single path segment only: /{code}
			.requestMatchers(HttpMethod.GET, "/*")
			.permitAll()
			.requestMatchers(HttpMethod.HEAD, "/*")
			.permitAll()
			.anyRequest()
			.denyAll())
			// The short link domain never gets a session (K20). The report form's CSRF token
			// therefore lives in its own cookie, sent only to /abuse and only first-party.
			.csrf((csrf) -> csrf.csrfTokenRepository(reportFormCsrfTokens(secureCookie)))
			.sessionManagement((sessions) -> sessions.sessionCreationPolicy(SessionCreationPolicy.NEVER))
			.headers((headers) -> headers.contentSecurityPolicy((csp) -> csp.policyDirectives(
					"default-src 'none'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")));
		return http.build();
	}

	private static CookieCsrfTokenRepository reportFormCsrfTokens(boolean secure) {
		CookieCsrfTokenRepository tokens = new CookieCsrfTokenRepository();
		tokens.setCookieName("VURLI_REPORT_CSRF");
		tokens.setCookiePath("/abuse");
		tokens.setCookieCustomizer((cookie) -> cookie.httpOnly(true).secure(secure).sameSite("Strict"));
		return tokens;
	}

}
