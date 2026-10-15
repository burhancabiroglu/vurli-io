package io.vurli.account;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import io.vurli.abuse.AuditLog;
import io.vurli.account.internal.SecretCipher;
import io.vurli.account.internal.Base32Codec;
import io.vurli.account.internal.Totp;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The second factor of administrators: a TOTP app (K47) or a one-time backup code (K49).
 * Codes are accepted with conditional updates, so two requests can never both use the
 * same code.
 */
@Service
public class TwoFactorAuthentication {

	static final int BACKUP_CODES = 10;

	/** 10 Base32 characters, 50 random bits, shown as XXXXX-XXXXX. */
	private static final int BACKUP_CODE_LENGTH = 10;

	private static final char[] BACKUP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

	public enum Method {

		TOTP, BACKUP_CODE

	}

	private final JdbcClient jdbc;

	private final SecretCipher cipher;

	private final AuditLog audit;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	public TwoFactorAuthentication(JdbcClient jdbc, SecretCipher cipher, AuditLog audit, Clock clock) {
		this.jdbc = jdbc;
		this.cipher = cipher;
		this.audit = audit;
		this.clock = clock;
	}

	/** The role as stored now, not as remembered in some session. */
	public boolean isAdmin(UUID userId) {
		return this.jdbc.sql("select count(*) from users where id = ? and role = 'ADMIN'")
			.param(userId)
			.query(Integer.class)
			.single() == 1;
	}

	public boolean isEnrolled(UUID userId) {
		return this.jdbc.sql("select count(*) from admin_credentials where user_id = ? and confirmed_at is not null")
			.param(userId)
			.query(Integer.class)
			.single() == 1;
	}

	/**
	 * The pending secret, created on first use. Reloading the setup page shows the same
	 * secret, so a code from an app that already scanned it keeps working.
	 * @throws IllegalStateException if a second factor is already set up
	 */
	@Transactional
	public TotpEnrollment enrollment(UUID userId, String email) {
		if (isEnrolled(userId)) {
			throw new IllegalStateException("Second factor already set up");
		}
		Optional<byte[]> pending = this.jdbc
			.sql("select totp_secret from admin_credentials where user_id = ? and confirmed_at is null")
			.param(userId)
			.query(byte[].class)
			.optional();
		byte[] secret;
		if (pending.isPresent()) {
			secret = this.cipher.decrypt(pending.get());
		}
		else {
			secret = new byte[20];
			this.random.nextBytes(secret);
			this.jdbc.sql("insert into admin_credentials (user_id, totp_secret, created_at) values (?, ?, ?)")
				.params(userId, this.cipher.encrypt(secret), now())
				.update();
		}
		String base32 = Base32Codec.encode(secret);
		String label = URLEncoder.encode("Vurli:" + email, StandardCharsets.UTF_8).replace("+", "%20");
		return new TotpEnrollment(base32,
				"otpauth://totp/" + label + "?secret=" + base32 + "&issuer=Vurli&algorithm=SHA1&digits=6&period=30");
	}

	/**
	 * Confirms the pending secret with a first code from the app and issues fresh backup
	 * codes, which are returned once and never again.
	 * @throws InvalidCodeException if the code does not match the pending secret
	 */
	@Transactional
	public List<String> confirmEnrollment(UUID userId, String code) {
		byte[] stored = this.jdbc.sql("select totp_secret from admin_credentials where user_id = ? and confirmed_at is null")
			.param(userId)
			.query(byte[].class)
			.optional()
			.orElseThrow(InvalidCodeException::new);
		OptionalLong step = Totp.verify(this.cipher.decrypt(stored), normalize(code), this.clock.instant(), -1);
		if (step.isEmpty()) {
			throw new InvalidCodeException();
		}
		this.jdbc.sql("update admin_credentials set confirmed_at = ?, last_used_step = ? where user_id = ?")
			.params(now(), step.getAsLong(), userId)
			.update();
		List<String> codes = newBackupCodes(userId);
		this.audit.record(userId, "ADMIN_TOTP_ENROLLED", "USER", userId.toString(), Map.of());
		return codes;
	}

	/**
	 * Accepts a TOTP code or an unused backup code.
	 * @return how the code was accepted, or empty if it was not
	 */
	@Transactional
	public Optional<Method> verify(UUID userId, String code) {
		String normalized = normalize(code);
		Optional<byte[]> stored = this.jdbc
			.sql("select totp_secret from admin_credentials where user_id = ? and confirmed_at is not null")
			.param(userId)
			.query(byte[].class)
			.optional();
		if (stored.isEmpty()) {
			return Optional.empty();
		}
		if (normalized.length() == 6) {
			OptionalLong step = Totp.verify(this.cipher.decrypt(stored.get()), normalized, this.clock.instant(),
					Long.MIN_VALUE);
			// The condition makes a used or older step lose, also against a concurrent request.
			if (step.isPresent() && this.jdbc
				.sql("update admin_credentials set last_used_step = ? where user_id = ? and last_used_step < ?")
				.params(step.getAsLong(), userId, step.getAsLong())
				.update() == 1) {
				return Optional.of(Method.TOTP);
			}
			return Optional.empty();
		}
		if (normalized.length() == BACKUP_CODE_LENGTH && this.jdbc
			.sql("update admin_backup_codes set used_at = ? where user_id = ? and code_hash = ? and used_at is null")
			.params(now(), userId, sha256(normalized))
			.update() == 1) {
			this.audit.record(userId, "ADMIN_BACKUP_CODE_USED", "USER", userId.toString(),
					Map.of("remaining", remainingBackupCodes(userId)));
			return Optional.of(Method.BACKUP_CODE);
		}
		return Optional.empty();
	}

	public int remainingBackupCodes(UUID userId) {
		return this.jdbc.sql("select count(*) from admin_backup_codes where user_id = ? and used_at is null")
			.param(userId)
			.query(Integer.class)
			.single();
	}

	private List<String> newBackupCodes(UUID userId) {
		this.jdbc.sql("delete from admin_backup_codes where user_id = ?").param(userId).update();
		List<String> codes = new ArrayList<>(BACKUP_CODES);
		for (int i = 0; i < BACKUP_CODES; i++) {
			StringBuilder code = new StringBuilder(BACKUP_CODE_LENGTH);
			for (int c = 0; c < BACKUP_CODE_LENGTH; c++) {
				code.append(BACKUP_ALPHABET[this.random.nextInt(BACKUP_ALPHABET.length)]);
			}
			this.jdbc.sql("insert into admin_backup_codes (user_id, code_hash, created_at) values (?, ?, ?)")
				.params(userId, sha256(code.toString()), now())
				.update();
			codes.add(code.substring(0, 5) + "-" + code.substring(5));
		}
		return codes;
	}

	/** Codes may be typed with spaces, dashes or in lower case. */
	private static String normalize(String code) {
		return (code == null) ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private Timestamp now() {
		return Timestamp.from(Instant.now(this.clock));
	}

}
