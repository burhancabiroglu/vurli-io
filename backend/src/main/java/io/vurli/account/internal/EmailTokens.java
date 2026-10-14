package io.vurli.account.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Single-use tokens sent by email. The raw token only ever exists in the email; the
 * database keeps its SHA-256, so a leaked table cannot verify addresses or reset
 * passwords.
 */
public class EmailTokens {

	public enum Purpose {

		VERIFY_EMAIL, RESET_PASSWORD

	}

	/** 32 random bytes, Base64url without padding. */
	private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

	private final JdbcClient jdbc;

	private final SecureRandom random = new SecureRandom();

	public EmailTokens(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Must run inside the transaction that creates or changes the account. */
	public String issue(UUID userId, Purpose purpose, Duration timeToLive, Instant now) {
		byte[] bytes = new byte[32];
		this.random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		this.jdbc.sql("""
				insert into email_tokens (id, user_id, purpose, token_hash, expires_at, created_at)
				values (:id, :userId, :purpose, :hash, :expiresAt, :now)""")
			.param("id", UUID.randomUUID())
			.param("userId", userId)
			.param("purpose", purpose.name())
			.param("hash", sha256(token))
			.param("expiresAt", utc(now.plus(timeToLive)))
			.param("now", utc(now))
			.update();
		return token;
	}

	/**
	 * Marks the token used and returns its user in a single statement, so two concurrent
	 * requests with the same token cannot both succeed.
	 */
	public Optional<UUID> consume(String token, Purpose purpose, Instant now) {
		if (token == null || !TOKEN.matcher(token).matches()) {
			return Optional.empty();
		}
		return this.jdbc.sql("""
				update email_tokens set used_at = :now
				where token_hash = :hash and purpose = :purpose and used_at is null and expires_at > :now
				returning user_id""")
			.param("now", utc(now))
			.param("hash", sha256(token))
			.param("purpose", purpose.name())
			.query(UUID.class)
			.optional();
	}

	/** After a password reset, older reset links must stop working too. */
	public void revokeAll(UUID userId, Purpose purpose, Instant now) {
		this.jdbc.sql("update email_tokens set used_at = :now where user_id = :userId and purpose = :purpose and used_at is null")
			.param("now", utc(now))
			.param("userId", userId)
			.param("purpose", purpose.name())
			.update();
	}

	public Optional<Instant> lastIssuedAt(UUID userId, Purpose purpose) {
		return this.jdbc.sql("select max(created_at) from email_tokens where user_id = :userId and purpose = :purpose")
			.param("userId", userId)
			.param("purpose", purpose.name())
			.query(OffsetDateTime.class)
			.optional()
			.map(OffsetDateTime::toInstant);
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	static byte[] sha256(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available", ex);
		}
	}

}
