package io.vurli.account;

/**
 * An email token is unknown, expired or already used. The three cases are deliberately
 * not told apart.
 */
public class InvalidTokenException extends RuntimeException {

	public InvalidTokenException() {
		super("Invalid or expired token");
	}

}
