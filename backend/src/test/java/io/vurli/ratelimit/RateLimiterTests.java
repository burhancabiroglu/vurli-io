package io.vurli.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import io.github.bucket4j.TimeMeter;
import io.vurli.ratelimit.internal.RateLimitProperties;
import org.junit.jupiter.api.Test;

class RateLimiterTests {

	private final ManualTime time = new ManualTime();

	private final RateLimiter limiter = new RateLimiter(properties(3, Duration.ofMinutes(1)), this.time);

	@Test
	void allowsTheCapacityThenRejectsWithTheExactWait() {
		for (int i = 0; i < 3; i++) {
			this.limiter.consume(RateLimit.LOGIN_PER_IP, "a");
		}

		// 3 per minute: one token comes back every 20 seconds.
		assertThatExceptionOfType(RateLimitExceededException.class)
			.isThrownBy(() -> this.limiter.consume(RateLimit.LOGIN_PER_IP, "a"))
			.satisfies((ex) -> assertThat(ex.retryAfterSeconds()).isEqualTo(20));

		this.time.advance(Duration.ofSeconds(15));
		assertThatExceptionOfType(RateLimitExceededException.class)
			.isThrownBy(() -> this.limiter.consume(RateLimit.LOGIN_PER_IP, "a"))
			.satisfies((ex) -> assertThat(ex.retryAfterSeconds()).isEqualTo(5));
	}

	@Test
	void tokensComeBackSmoothlyUpToTheCapacity() {
		for (int i = 0; i < 3; i++) {
			this.limiter.consume(RateLimit.LOGIN_PER_IP, "a");
		}

		this.time.advance(Duration.ofSeconds(20));
		assertThat(this.limiter.tryConsume(RateLimit.LOGIN_PER_IP, "a")).isTrue();
		assertThat(this.limiter.tryConsume(RateLimit.LOGIN_PER_IP, "a")).isFalse();

		// A long pause refills to the capacity, never beyond.
		this.time.advance(Duration.ofHours(1));
		for (int i = 0; i < 3; i++) {
			assertThat(this.limiter.tryConsume(RateLimit.LOGIN_PER_IP, "a")).isTrue();
		}
		assertThat(this.limiter.tryConsume(RateLimit.LOGIN_PER_IP, "a")).isFalse();
	}

	@Test
	void waitsOfLessThanASecondAreRoundedUp() {
		assertThat(new RateLimitExceededException(Duration.ofMillis(1)).retryAfterSeconds()).isEqualTo(1);
		assertThat(new RateLimitExceededException(Duration.ofMillis(5001)).retryAfterSeconds()).isEqualTo(6);
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

	private static final class ManualTime implements TimeMeter {

		private long nanos;

		void advance(Duration duration) {
			this.nanos += duration.toNanos();
		}

		@Override
		public long currentTimeNanos() {
			return this.nanos;
		}

		@Override
		public boolean isWallClockBased() {
			return false;
		}

	}

	private static RateLimitProperties properties(int capacity, Duration period) {
		Map<RateLimit, RateLimitProperties.Limit> limits = new EnumMap<>(RateLimit.class);
		for (RateLimit limit : RateLimit.values()) {
			limits.put(limit, new RateLimitProperties.Limit(capacity, period));
		}
		return new RateLimitProperties(limits);
	}

}
