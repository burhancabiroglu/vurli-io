package io.vurli.account;

/**
 * Length is the only rule: composition rules make passwords harder to remember without
 * making them meaningfully stronger.
 */
public class WeakPasswordException extends RuntimeException {

	public enum Reason {

		TOO_SHORT, TOO_LONG

	}

	private final Reason reason;

	public WeakPasswordException(Reason reason) {
		super("Password rejected: " + reason);
		this.reason = reason;
	}

	public Reason reason() {
		return this.reason;
	}

}
