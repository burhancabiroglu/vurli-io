package io.vurli.link;

/**
 * The requested custom alias does not follow the alias rules.
 */
public class InvalidAliasException extends RuntimeException {

	public enum Reason {

		FORMAT, RESERVED

	}

	private final Reason reason;

	public InvalidAliasException(Reason reason) {
		super("Invalid alias: " + reason);
		this.reason = reason;
	}

	public Reason reason() {
		return this.reason;
	}

}
