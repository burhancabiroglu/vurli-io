package io.vurli.link.internal;

import java.security.SecureRandom;

/**
 * Generates unpredictable Base62 codes. Seven characters give 62^7 (about 3.5 trillion)
 * combinations, so guessing codes to discover other people's links is impractical.
 */
public class RandomCodeGenerator implements CodeGenerator {

	static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

	static final int LENGTH = 7;

	private final SecureRandom random = new SecureRandom();

	@Override
	public String next() {
		char[] code = new char[LENGTH];
		for (int i = 0; i < LENGTH; i++) {
			code[i] = ALPHABET.charAt(this.random.nextInt(ALPHABET.length()));
		}
		return new String(code);
	}

}
