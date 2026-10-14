package io.vurli;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Short links and the health check are public; everything else requires a login.
 * Account management replaces the default login in M2.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((requests) -> requests
			.requestMatchers(HttpMethod.GET, "/actuator/health")
			.permitAll()
			// Single path segment only: /{code}
			.requestMatchers(HttpMethod.GET, "/*")
			.permitAll()
			.requestMatchers(HttpMethod.HEAD, "/*")
			.permitAll()
			.anyRequest()
			.authenticated()).formLogin(Customizer.withDefaults());
		return http.build();
	}

}
