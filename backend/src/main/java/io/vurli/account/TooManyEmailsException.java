package io.vurli.account;

import java.time.Duration;

/**
 * A verification email was sent moments ago; sending another would only flood the inbox.
 */
public class TooManyEmailsException extends RuntimeException {

	private final Duration retryAfter;

	public TooManyEmailsException(Duration retryAfter) {
		super("Email sent recently");
		this.retryAfter = retryAfter;
	}

	public Duration retryAfter() {
		return this.retryAfter;
	}

}
