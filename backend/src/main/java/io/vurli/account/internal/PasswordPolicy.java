package io.vurli.account.internal;

import io.vurli.account.WeakPasswordException;
import io.vurli.account.WeakPasswordException.Reason;

/**
 * At least 10 characters (NIST SP 800-63B favours length over composition rules). The
 * upper bound keeps Argon2 hashing from being used to burn CPU.
 */
public final class PasswordPolicy {

	static final int MIN_LENGTH = 10;

	static final int MAX_LENGTH = 128;

	private PasswordPolicy() {
	}

	public static void check(String password) {
		int length = (password != null) ? password.codePointCount(0, password.length()) : 0;
		if (length < MIN_LENGTH) {
			throw new WeakPasswordException(Reason.TOO_SHORT);
		}
		if (length > MAX_LENGTH) {
			throw new WeakPasswordException(Reason.TOO_LONG);
		}
	}

}
