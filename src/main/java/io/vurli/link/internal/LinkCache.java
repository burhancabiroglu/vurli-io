package io.vurli.link.internal;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

/**
 * Caches link snapshots by code, including misses, so that scanning random codes does
 * not hit the database.
 * <p>
 * Every change to a link must call {@link #invalidate} after its transaction commits.
 * Caffeine loads a key atomically and an invalidation waits for an in-flight load of the
 * same key, so a load that read the old row before the commit cannot put that old row
 * back into the cache afterwards. The TTL only bounds memory and staleness from changes
 * made outside the application.
 */
public class LinkCache {

	private final Cache<String, Optional<LinkSnapshot>> cache;

	private final Function<String, Optional<LinkSnapshot>> loader;

	public LinkCache(Function<String, Optional<LinkSnapshot>> loader, Duration timeToLive, long maximumSize,
			Ticker ticker) {
		this.loader = loader;
		this.cache = Caffeine.newBuilder()
			.expireAfterWrite(timeToLive)
			.maximumSize(maximumSize)
			.ticker(ticker)
			.build();
	}

	public Optional<LinkSnapshot> get(String code) {
		return this.cache.get(code, this.loader);
	}

	public void invalidate(String code) {
		this.cache.invalidate(code);
	}

}
