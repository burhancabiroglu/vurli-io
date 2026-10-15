package io.vurli.account;

/** A second-factor code that is wrong, expired or already used. */
public class InvalidCodeException extends RuntimeException {

	public InvalidCodeException() {
		super("Invalid second-factor code");
	}

}
