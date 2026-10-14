package io.vurli.link;

/**
 * The requested expiry is not in the future.
 */
public class InvalidExpiryException extends RuntimeException {

	public InvalidExpiryException() {
		super("Expiry must be in the future");
	}

}
