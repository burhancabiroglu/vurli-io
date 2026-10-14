package io.vurli.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.vurli.Mailpit;
import io.vurli.Mailpit.Email;
import io.vurli.TestAccounts;
import io.vurli.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountManagementIntegrationTests {

	private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

	@Autowired
	private AccountManagement accounts;

	@Autowired
	private Mailpit mailpit;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void registrationCreatesUnverifiedAccountWithWorkspaceAndTurkishEmail() {
		String email = TestAccounts.uniqueEmail();

		this.accounts.register("  " + email.toUpperCase(Locale.ROOT) + " ", TestAccounts.PASSWORD, TURKISH);

		assertThat(this.jdbc.queryForObject(
				"select count(*) from users where email = ? and email_verified_at is null and locale = 'tr'",
				Integer.class, email))
			.isOne();
		assertThat(this.jdbc.queryForObject(
				"select count(*) from workspaces w join users u on u.id = w.owner_id where u.email = ?",
				Integer.class, email))
			.isOne();
		Email message = this.mailpit.latestTo(email);
		assertThat(message.subject()).isEqualTo("Vurli için e-posta adresini doğrula");
		assertThat(message.from()).isEqualTo("no-reply@vurli.io");
		assertThat(message.text()).contains("http://app.test/verify-email#token=");
		assertThat(message.token()).isPresent();
	}

	@Test
	void unsupportedLanguagesGetEnglish() {
		String email = TestAccounts.uniqueEmail();

		this.accounts.register(email, TestAccounts.PASSWORD, Locale.GERMAN);

		assertThat(this.mailpit.latestTo(email).subject()).isEqualTo("Confirm your email for Vurli");
	}

	@Test
	void verificationTokenWorksOnce() {
		String email = TestAccounts.registerUnverified(this.accounts);
		String token = this.mailpit.latestTo(email).token().orElseThrow();

		this.accounts.verifyEmail(token);

		assertThat(verified(email)).isTrue();
		assertThatExceptionOfType(InvalidTokenException.class).isThrownBy(() -> this.accounts.verifyEmail(token));
	}

	@Test
	void expiredTokenIsRejected() {
		String email = TestAccounts.registerUnverified(this.accounts);
		String token = this.mailpit.latestTo(email).token().orElseThrow();
		this.jdbc.update("update email_tokens set expires_at = now() - interval '1 second' where token_hash = ?",
				(Object) sha256(token));

		assertThatExceptionOfType(InvalidTokenException.class).isThrownBy(() -> this.accounts.verifyEmail(token));
		assertThat(verified(email)).isFalse();
	}

	@Test
	void malformedOrUnknownTokensAreRejectedTheSameWay() {
		for (String token : new String[] { null, "", "short", "x".repeat(43), "' or 1=1 --" }) {
			assertThatExceptionOfType(InvalidTokenException.class).isThrownBy(() -> this.accounts.verifyEmail(token));
		}
	}

	@Test
	void onlyTheTokenHashIsStored() {
		String email = TestAccounts.registerUnverified(this.accounts);
		String token = this.mailpit.latestTo(email).token().orElseThrow();

		byte[] stored = this.jdbc.queryForObject(
				"select t.token_hash from email_tokens t join users u on u.id = t.user_id where u.email = ?",
				byte[].class, email);

		assertThat(stored).isEqualTo(sha256(token)).hasSize(32);
	}

	@Test
	void registeringAVerifiedAddressAgainChangesNothingAndNotifiesTheOwner() {
		String email = TestAccounts.registerVerified(this.accounts, this.mailpit);

		assertThatNoException()
			.isThrownBy(() -> this.accounts.register(email, "another password 123", Locale.ENGLISH));

		assertThat(this.jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email))
			.isOne();
		String hash = this.jdbc.queryForObject("select password_hash from users where email = ?", String.class,
				email);
		assertThat(this.passwordEncoder.matches(TestAccounts.PASSWORD, hash)).isTrue();
		assertThat(this.mailpit.latestTo(email).subject()).isEqualTo("Your Vurli account");
	}

	@Test
	void registeringAnUnverifiedAddressAgainDoesNotFloodTheInbox() {
		String email = TestAccounts.registerUnverified(this.accounts);
		this.mailpit.latestTo(email);

		this.accounts.register(email, TestAccounts.PASSWORD, Locale.ENGLISH);

		assertThat(this.mailpit.messagesTo(email)).hasSize(1);
	}

	@Test
	void concurrentRegistrationsCreateExactlyOneAccount() throws Exception {
		String email = TestAccounts.uniqueEmail();
		int requests = 8;
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> results = new ArrayList<>();
		try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
			for (int i = 0; i < requests; i++) {
				results.add(executor.submit(() -> {
					start.await();
					this.accounts.register(email, TestAccounts.PASSWORD, Locale.ENGLISH);
					return null;
				}));
			}
			start.countDown();
			for (Future<?> result : results) {
				result.get(60, TimeUnit.SECONDS);
			}
		}

		assertThat(this.jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email))
			.isOne();
		assertThat(this.jdbc.queryForObject(
				"select count(*) from workspaces w join users u on u.id = w.owner_id where u.email = ?",
				Integer.class, email))
			.isOne();
	}

	@Test
	void resendIsLimitedToOncePerMinute() {
		String email = TestAccounts.registerUnverified(this.accounts);
		UUID userId = userId(email);

		assertThatExceptionOfType(TooManyEmailsException.class)
			.isThrownBy(() -> this.accounts.resendVerification(userId));

		this.jdbc.update("update email_tokens set created_at = created_at - interval '61 seconds' where user_id = ?",
				userId);
		this.accounts.resendVerification(userId);
		assertThat(this.mailpit.messagesTo(email)).hasSize(2);
	}

	@Test
	void passwordResetForUnknownAddressSendsNothing() {
		String email = TestAccounts.uniqueEmail();

		assertThatNoException().isThrownBy(() -> this.accounts.requestPasswordReset(email));

		assertThat(this.mailpit.messagesTo(email)).isEmpty();
	}

	@Test
	void resetInvalidatesOlderResetLinksAndVerifiesTheAddress() {
		String email = TestAccounts.registerUnverified(this.accounts);
		this.accounts.requestPasswordReset(email);
		String firstToken = this.mailpit.latestTo(email).token().orElseThrow();
		this.jdbc.update(
				"update email_tokens set created_at = created_at - interval '61 seconds' where user_id = ? and purpose = 'RESET_PASSWORD'",
				userId(email));
		this.accounts.requestPasswordReset(email);
		String secondToken = this.mailpit.latestTo(email).token().orElseThrow();

		this.accounts.resetPassword(secondToken, "a brand new password");

		assertThat(verified(email)).isTrue();
		assertThatExceptionOfType(InvalidTokenException.class)
			.isThrownBy(() -> this.accounts.resetPassword(firstToken, "yet another password"));
		assertThatExceptionOfType(InvalidTokenException.class)
			.isThrownBy(() -> this.accounts.resetPassword(secondToken, "yet another password"));
	}

	@Test
	void weakPasswordsAndBadAddressesAreRejected() {
		assertThatExceptionOfType(WeakPasswordException.class)
			.isThrownBy(() -> this.accounts.register(TestAccounts.uniqueEmail(), "short", Locale.ENGLISH))
			.satisfies((ex) -> assertThat(ex.reason()).isEqualTo(WeakPasswordException.Reason.TOO_SHORT));
		assertThatExceptionOfType(WeakPasswordException.class)
			.isThrownBy(() -> this.accounts.register(TestAccounts.uniqueEmail(), "a".repeat(129), Locale.ENGLISH))
			.satisfies((ex) -> assertThat(ex.reason()).isEqualTo(WeakPasswordException.Reason.TOO_LONG));
		for (String email : new String[] { "", "no-at-sign", "a@b", "a b@example.com", "a@@example.com" }) {
			assertThatExceptionOfType(InvalidEmailException.class)
				.isThrownBy(() -> this.accounts.register(email, TestAccounts.PASSWORD, Locale.ENGLISH));
		}
	}

	private boolean verified(String email) {
		return this.jdbc.queryForObject("select email_verified_at is not null from users where email = ?",
				Boolean.class, email);
	}

	private UUID userId(String email) {
		return this.jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
	}

	private static byte[] sha256(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
