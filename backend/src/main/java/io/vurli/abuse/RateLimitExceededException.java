package io.vurli.abuse;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {

	private final Duration retryAfter;

	public RateLimitExceededException(Duration retryAfter) {
		super("Rate limit exceeded");
		this.retryAfter = retryAfter;
	}

	/** At least one second, for the {@code Retry-After} header. */
	public long retryAfterSeconds() {
		return Math.max(1, (this.retryAfter.toMillis() + 999) / 1000);
	}

}
