package io.vurli.account.internal;

import java.util.Locale;
import java.util.regex.Pattern;

import io.vurli.account.InvalidEmailException;

/**
 * Normalizes addresses so that "Ali@Example.com" and "ali@example.com" are one account.
 * Validation stays loose on purpose: the verification email is the real check.
 */
public final class EmailAddress {

	static final int MAX_LENGTH = 254;

	private static final Pattern SHAPE = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	private EmailAddress() {
	}

	public static String normalize(String input) {
		if (input == null) {
			throw new InvalidEmailException();
		}
		String email = input.strip().toLowerCase(Locale.ROOT);
		if (email.length() > MAX_LENGTH || !SHAPE.matcher(email).matches()
				|| email.chars().anyMatch((c) -> c < 0x20 || c == 0x7f)) {
			throw new InvalidEmailException();
		}
		return email;
	}

}
