package io.vurli.abuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import io.vurli.abuse.internal.RateLimitProperties;
import org.junit.jupiter.api.Test;

class RateLimiterTests {

	private final RateLimiter limiter = new RateLimiter(properties(3, Duration.ofMinutes(1)));

	@Test
	void allowsTheCapacityThenRejectsWithAWait() {
		for (int i = 0; i < 3; i++) {
			this.limiter.consume(RateLimit.LOGIN_PER_IP, "a");
		}

		assertThatExceptionOfType(RateLimitExceededException.class)
			.isThrownBy(() -> this.limiter.consume(RateLimit.LOGIN_PER_IP, "a"))
			// One token per 20 seconds comes back.
			.satisfies((ex) -> assertThat(ex.retryAfterSeconds()).isBetween(1L, 20L));
	}

	@Test
	void keysAndLimitsAreIndependent() {
		for (int i = 0; i < 3; i++) {
			this.limiter.consume(RateLimit.LOGIN_PER_IP, "a");
		}

		assertThat(this.limiter.tryConsume(RateLimit.LOGIN_PER_IP, "a")).isFalse();
		assertThat(this.limiter.tryConsume(RateLimit.LOGIN_PER_IP, "b")).isTrue();
		assertThat(this.limiter.tryConsume(RateLimit.EMAILS_PER_IP, "a")).isTrue();
	}

	@Test
	void checkDoesNotUseTokens() {
		for (int i = 0; i < 10; i++) {
			this.limiter.check(RateLimit.LOGIN_FAILURES_PER_ACCOUNT, "user@example.com");
		}
		for (int i = 0; i < 3; i++) {
			this.limiter.record(RateLimit.LOGIN_FAILURES_PER_ACCOUNT, "user@example.com");
		}

		assertThatExceptionOfType(RateLimitExceededException.class)
			.isThrownBy(() -> this.limiter.check(RateLimit.LOGIN_FAILURES_PER_ACCOUNT, "user@example.com"));
		assertThatNoException().isThrownBy(() -> this.limiter.record(RateLimit.LOGIN_FAILURES_PER_ACCOUNT, "user@example.com"));
	}

	@Test
	void everyLimitMustBeConfigured() {
		Map<RateLimit, RateLimitProperties.Limit> partial = new EnumMap<>(RateLimit.class);
		partial.put(RateLimit.LOGIN_PER_IP, new RateLimitProperties.Limit(1, Duration.ofMinutes(1)));

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> new RateLimitProperties(partial))
			.withMessageContaining("LOGIN_FAILURES_PER_ACCOUNT");
		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> new RateLimitProperties.Limit(0, Duration.ofMinutes(1)));
	}

	private static RateLimitProperties properties(int capacity, Duration period) {
		Map<RateLimit, RateLimitProperties.Limit> limits = new EnumMap<>(RateLimit.class);
		for (RateLimit limit : RateLimit.values()) {
			limits.put(limit, new RateLimitProperties.Limit(capacity, period));
		}
		return new RateLimitProperties(limits);
	}

}
