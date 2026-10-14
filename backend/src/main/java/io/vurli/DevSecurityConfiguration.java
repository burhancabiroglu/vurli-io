package io.vurli;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Opens the development API without login. Exists only in the dev profile; without it,
 * {@code /dev/api/**} falls under {@link SecurityConfiguration} and requires a login.
 * Removed when real accounts arrive (M2).
 */
@Profile("dev")
@Configuration(proxyBeanMethods = false)
class DevSecurityConfiguration {

	@Bean
	@Order(0)
	SecurityFilterChain devApiSecurityFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher("/dev/api/**")
			.authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

}
