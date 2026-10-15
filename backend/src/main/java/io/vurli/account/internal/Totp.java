package io.vurli.account.internal;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords (RFC 6238 over RFC 4226), the variant every
 * authenticator app supports: HMAC-SHA1, 30-second steps (K47).
 */
public final class Totp {

	static final long STEP_SECONDS = 30;

	static final int DIGITS = 6;

	/** One step either side, for clock drift between the phone and the server. */
	static final int WINDOW = 1;

	private Totp() {
	}

	/** The code for a time step, left-padded to the given number of digits. */
	static String code(byte[] key, long step, int digits) {
		byte[] hash;
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(key, "HmacSHA1"));
			hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA1 unavailable", ex);
		}
		// Dynamic truncation (RFC 4226 section 5.3).
		int offset = hash[hash.length - 1] & 0x0f;
		int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
				| ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
		int otp = binary % (int) Math.pow(10, digits);
		return String.format("%0" + digits + "d", otp);
	}

	static long step(Instant time) {
		return Math.floorDiv(time.getEpochSecond(), STEP_SECONDS);
	}

	/**
	 * Finds the step a code belongs to, within the drift window. Steps up to and including
	 * {@code lastUsedStep} are refused, so an observed code cannot be replayed.
	 * @return the matching step, or empty
	 */
	public static OptionalLong verify(byte[] key, String code, Instant now, long lastUsedStep) {
		if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
			return OptionalLong.empty();
		}
		byte[] given = code.getBytes(StandardCharsets.US_ASCII);
		long current = step(now);
		for (long step = current - WINDOW; step <= current + WINDOW; step++) {
			byte[] expected = code(key, step, DIGITS).getBytes(StandardCharsets.US_ASCII);
			// Constant time, and every step is checked, so timing reveals nothing.
			if (MessageDigest.isEqual(expected, given) && step > lastUsedStep) {
				return OptionalLong.of(step);
			}
		}
		return OptionalLong.empty();
	}

}
