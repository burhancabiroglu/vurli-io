package io.vurli.account;

public class InvalidEmailException extends RuntimeException {

	public InvalidEmailException() {
		super("Invalid email address");
	}

}
