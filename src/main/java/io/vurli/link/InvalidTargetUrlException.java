package io.vurli.link;

/**
 * The requested target URL is not accepted as a redirect destination.
 */
public class InvalidTargetUrlException extends RuntimeException {

	public enum Reason {

		BLANK, TOO_LONG, ILLEGAL_CHARACTER, MALFORMED, UNSUPPORTED_SCHEME, CREDENTIALS, IP_ADDRESS, INVALID_HOST,
		NON_DEFAULT_PORT, SELF_REFERENCE

	}

	private final Reason reason;

	public InvalidTargetUrlException(Reason reason) {
		super("Invalid target URL: " + reason);
		this.reason = reason;
	}

	public Reason reason() {
		return this.reason;
	}

}
