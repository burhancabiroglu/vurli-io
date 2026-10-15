package io.vurli.ratelimit.internal;

import java.time.Duration;
import java.util.Map;

import io.vurli.ratelimit.RateLimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code vurli.rate-limits.<limit>.capacity} and {@code .period}, e.g.
 * {@code vurli.rate-limits.login-per-ip.capacity=10}. Every limit must be configured; a
 * missing one stops the application instead of silently running without a limit.
 */
@ConfigurationProperties("vurli")
public record RateLimitProperties(Map<RateLimit, Limit> rateLimits) {

	public RateLimitProperties {
		for (RateLimit limit : RateLimit.values()) {
			if (rateLimits == null || !rateLimits.containsKey(limit)) {
				throw new IllegalStateException("Missing vurli.rate-limits for " + limit);
			}
		}
	}

	public Limit limit(RateLimit limit) {
		return this.rateLimits.get(limit);
	}

	public record Limit(int capacity, Duration period) {

		public Limit {
			if (capacity < 1 || period == null || period.isNegative() || period.isZero()) {
				throw new IllegalStateException("A rate limit needs a positive capacity and period");
			}
		}

	}

}
