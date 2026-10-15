package io.vurli.account.internal;

import java.time.Clock;
import java.util.Map;

import io.vurli.abuse.AuditLog;
import io.vurli.account.VurliUser;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ AccountProperties.class, AdminProperties.class })
class AccountConfiguration {

	/**
	 * Argon2id with OWASP's minimum (19 MiB memory, 2 iterations, 1 lane). Hashes are
	 * stored with an {@code {argon2}} prefix so the algorithm can be upgraded later
	 * without invalidating existing passwords.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
		return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2));
	}

	@Bean
	UserDetailsService userDetailsService(UserAccountRepository users, WorkspaceRepository workspaces) {
		return (username) -> {
			String email;
			try {
				email = EmailAddress.normalize(username);
			}
			catch (RuntimeException ex) {
				throw new UsernameNotFoundException("Unknown user");
			}
			UserAccount user = users.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
			Workspace workspace = workspaces.findFirstByOwnerIdOrderByCreatedAt(user.id())
				.orElseThrow(() -> new IllegalStateException("Account without workspace: " + user.id()));
			return new VurliUser(user.id(), workspace.id(), user.email(), user.role(), user.passwordHash());
		};
	}

	/**
	 * Unknown emails and wrong passwords fail the same way and, thanks to Spring
	 * Security's dummy password check, take about the same time.
	 */
	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	/** Fails at startup without a valid key, rather than at the first administrator login. */
	@Bean
	SecretCipher secretCipher(AdminProperties properties) {
		return new SecretCipher(properties.totpKey());
	}

	@Bean
	AdminBootstrap adminBootstrap(AdminProperties properties, JdbcClient jdbc, AuditLog audit, Clock clock) {
		return new AdminBootstrap(properties, jdbc, audit, clock);
	}

	@Bean
	EmailTokens emailTokens(JdbcClient jdbc) {
		return new EmailTokens(jdbc);
	}

	@Bean
	AccountMailer accountMailer(JavaMailSender mailSender, MessageSource messages, AccountProperties properties) {
		return new AccountMailer(mailSender, messages, properties);
	}

}
