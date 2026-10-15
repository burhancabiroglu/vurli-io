package io.vurli.account.internal;

import java.time.Instant;
import java.util.Locale;

/** Computes the code an authenticator app would show, for tests in other packages. */
public final class TestTotp {

	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

	private TestTotp() {
	}

	public static String codeNow(String base32Secret) {
		return Totp.code(decode(base32Secret), Totp.step(Instant.now()), Totp.DIGITS);
	}

	static byte[] decode(String base32) {
		String clean = base32.trim().toUpperCase(Locale.ROOT);
		byte[] out = new byte[clean.length() * 5 / 8];
		int buffer = 0;
		int bits = 0;
		int index = 0;
		for (char c : clean.toCharArray()) {
			buffer = (buffer << 5) | ALPHABET.indexOf(c);
			bits += 5;
			if (bits >= 8) {
				out[index++] = (byte) (buffer >> (bits - 8));
				bits -= 8;
			}
		}
		return out;
	}

}
