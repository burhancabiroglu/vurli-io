package io.vurli.abuse;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.TimeMeter;
import io.vurli.abuse.internal.RateLimitProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Token buckets per limit and key (an address, an email, a user id), kept in memory: one
 * instance, and a restart forgiving everyone is acceptable (K42). Buckets refill
 * smoothly, so "10 per minute" allows a burst of 10 and then one every 6 seconds.
 * <p>
 * Idle buckets are dropped after a full period, when they would be full again anyway.
 * The number of buckets per limit is capped so a flood of new keys cannot exhaust memory;
 * at the cap the least recently used are dropped first.
 */
@Service
public class RateLimiter {

	static final int MAX_KEYS_PER_LIMIT = 200_000;

	private final Map<RateLimit, Limiter> limiters = new EnumMap<>(RateLimit.class);

	@Autowired
	public RateLimiter(RateLimitProperties properties) {
		this(properties, TimeMeter.SYSTEM_NANOTIME);
	}

	/** With a controllable clock, so tests can check refills and waits exactly. */
	RateLimiter(RateLimitProperties properties, TimeMeter time) {
		for (RateLimit limit : RateLimit.values()) {
			RateLimitProperties.Limit config = properties.limit(limit);
			this.limiters.put(limit, new Limiter(config.capacity(), config.period(), time));
		}
	}

	/**
	 * Takes one token.
	 * @throws RateLimitExceededException if none is left
	 */
	public void consume(RateLimit limit, String key) {
		ConsumptionProbe probe = this.limiters.get(limit).bucket(key).tryConsumeAndReturnRemaining(1);
		if (!probe.isConsumed()) {
			throw new RateLimitExceededException(Duration.ofNanos(probe.getNanosToWaitForRefill()));
		}
	}

	/** Takes one token if there is one; for callers that answer without an exception. */
	public boolean tryConsume(RateLimit limit, String key) {
		return this.limiters.get(limit).bucket(key).tryConsume(1);
	}

	/**
	 * Fails if no token is left, without taking one. For limits that only count failures:
	 * check first, record the failure afterwards.
	 * @throws RateLimitExceededException if none is left
	 */
	public void check(RateLimit limit, String key) {
		EstimationProbe probe = this.limiters.get(limit).bucket(key).estimateAbilityToConsume(1);
		if (!probe.canBeConsumed()) {
			throw new RateLimitExceededException(Duration.ofNanos(probe.getNanosToWaitForRefill()));
		}
	}

	/** Records one occurrence, such as a failed login; never fails. */
	public void record(RateLimit limit, String key) {
		this.limiters.get(limit).bucket(key).tryConsume(1);
	}

	private static final class Limiter {

		private final Bandwidth bandwidth;

		private final TimeMeter time;

		private final Cache<String, Bucket> buckets;

		Limiter(int capacity, Duration period, TimeMeter time) {
			this.time = time;
			this.bandwidth = Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build();
			this.buckets = Caffeine.newBuilder().expireAfterAccess(period).maximumSize(MAX_KEYS_PER_LIMIT).build();
		}

		Bucket bucket(String key) {
			return this.buckets.get(key,
					(k) -> Bucket.builder().addLimit(this.bandwidth).withCustomTimePrecision(this.time).build());
		}

	}

}
