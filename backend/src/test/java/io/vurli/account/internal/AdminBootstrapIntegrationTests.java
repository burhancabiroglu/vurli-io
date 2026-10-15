package io.vurli.account.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import io.vurli.Mailpit;
import io.vurli.TestAccounts;
import io.vurli.TestcontainersConfiguration;
import io.vurli.abuse.AuditLog;
import io.vurli.account.AccountManagement;
import io.vurli.account.TwoFactorAuthentication;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AdminBootstrapIntegrationTests {

	@Autowired
	private AccountManagement accounts;

	@Autowired
	private TwoFactorAuthentication twoFactor;

	@Autowired
	private Mailpit mailpit;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AuditLog audit;

	@Autowired
	private Clock clock;

	@Test
	void promotesAVerifiedAccountOnceAndRecordsIt() {
		String email = TestAccounts.registerVerified(this.accounts, this.mailpit);

		run(email.toUpperCase(), "");
		run(email, "");

		assertThat(role(email)).isEqualTo("ADMIN");
		assertThat(systemActions(email, "ADMIN_PROMOTED")).isEqualTo(1);
	}

	@Test
	void neverPromotesAnUnverifiedOrUnknownAccount() {
		String unverified = TestAccounts.registerUnverified(this.accounts);

		run(unverified, "");
		run(TestAccounts.uniqueEmail(), "");

		assertThat(role(unverified)).isEqualTo("USER");
		assertThat(systemActions(unverified, "ADMIN_PROMOTED")).isZero();
	}

	@Test
	void resetRemovesTheSecondFactorAndItsBackupCodes() {
		String email = TestAccounts.registerVerified(this.accounts, this.mailpit);
		run(email, "");
		UUID userId = this.jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
		String secret = this.twoFactor.enrollment(userId, email).secret();
		this.twoFactor.confirmEnrollment(userId, TestTotp.codeNow(secret));
		assertThat(this.twoFactor.isEnrolled(userId)).isTrue();

		run("", email);

		assertThat(this.twoFactor.isEnrolled(userId)).isFalse();
		assertThat(this.twoFactor.remainingBackupCodes(userId)).isZero();
		assertThat(systemActions(email, "ADMIN_TOTP_RESET")).isEqualTo(1);
		// A fresh setup starts with a new secret.
		assertThat(this.twoFactor.enrollment(userId, email).secret()).isNotEqualTo(secret);
	}

	@Test
	void resetIgnoresAccountsThatAreNotAdministrators() {
		String email = TestAccounts.registerVerified(this.accounts, this.mailpit);

		run("", email);

		assertThat(systemActions(email, "ADMIN_TOTP_RESET")).isZero();
	}

	private void run(String promote, String resetTotp) {
		new AdminBootstrap(new AdminProperties("unused", promote, resetTotp, Duration.ofHours(1)), this.jdbcClient,
				this.audit, this.clock)
			.run(new DefaultApplicationArguments());
	}

	private String role(String email) {
		return this.jdbc.queryForObject("select role from users where email = ?", String.class, email);
	}

	/** Actions of the system itself: no actor, the account as target. */
	private int systemActions(String email, String action) {
		return this.jdbc.queryForObject("""
				select count(*) from audit_log a join users u on a.target_id = u.id::text
				where u.email = ? and a.action = ? and a.actor_id is null""", Integer.class, email, action);
	}

}
