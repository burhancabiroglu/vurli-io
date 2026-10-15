package io.vurli.link.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.github.benmanes.caffeine.cache.Ticker;
import io.vurli.link.LinkStatus;
import org.junit.jupiter.api.Test;

class LinkCacheTests {

	private static final LinkSnapshot ACTIVE = new LinkSnapshot(UUID.randomUUID(), "https://example.com", LinkStatus.ACTIVE, null, null);

	private static final LinkSnapshot BLOCKED = new LinkSnapshot(UUID.randomUUID(), "https://example.com", LinkStatus.BLOCKED, null,
			null);

	/**
	 * A load reads the row before a block commits and finishes after it. The invalidation
	 * that follows the commit must not leave the old row in the cache.
	 */
	@Test
	void loadThatReadBeforeBlockCannotCacheTheOldRow() throws Exception {
		AtomicReference<LinkSnapshot> database = new AtomicReference<>(ACTIVE);
		AtomicInteger loads = new AtomicInteger();
		CountDownLatch firstReadDone = new CountDownLatch(1);
		CountDownLatch releaseFirstLoad = new CountDownLatch(1);
		LinkCache cache = new LinkCache((code) -> {
			LinkSnapshot row = database.get();
			if (loads.incrementAndGet() == 1) {
				firstReadDone.countDown();
				await(releaseFirstLoad);
			}
			return Optional.of(row);
		}, Duration.ofMinutes(10), 100, Ticker.systemTicker());

		Thread visitor = Thread.ofPlatform().start(() -> cache.get("kod1234"));
		assertThat(firstReadDone.await(5, TimeUnit.SECONDS)).isTrue();

		database.set(BLOCKED); // the block commits
		Thread blocker = Thread.ofPlatform().start(() -> cache.invalidate("kod1234"));
		waitUntilWaitingOrDone(blocker);
		releaseFirstLoad.countDown();
		visitor.join(5_000);
		blocker.join(5_000);

		assertThat(cache.get("kod1234")).contains(BLOCKED);
	}

	@Test
	void cachesMissesUntilInvalidated() {
		AtomicReference<LinkSnapshot> database = new AtomicReference<>();
		AtomicInteger loads = new AtomicInteger();
		LinkCache cache = new LinkCache((code) -> {
			loads.incrementAndGet();
			return Optional.ofNullable(database.get());
		}, Duration.ofMinutes(10), 100, Ticker.systemTicker());

		assertThat(cache.get("yeni-kod")).isEmpty();
		assertThat(cache.get("yeni-kod")).isEmpty();
		assertThat(loads).hasValue(1);

		database.set(ACTIVE);
		cache.invalidate("yeni-kod");

		assertThat(cache.get("yeni-kod")).contains(ACTIVE);
	}

	@Test
	void entriesExpireAfterTimeToLive() {
		AtomicReference<LinkSnapshot> database = new AtomicReference<>(ACTIVE);
		AtomicReference<Long> nanos = new AtomicReference<>(0L);
		LinkCache cache = new LinkCache((code) -> Optional.of(database.get()), Duration.ofSeconds(60), 100,
				nanos::get);
		cache.get("kod1234");

		database.set(BLOCKED); // changed outside the application, without invalidation
		nanos.set(Duration.ofSeconds(59).toNanos());
		assertThat(cache.get("kod1234")).contains(ACTIVE);

		nanos.set(Duration.ofSeconds(60).toNanos());
		assertThat(cache.get("kod1234")).contains(BLOCKED);
	}

	private static void waitUntilWaitingOrDone(Thread thread) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			Thread.State state = thread.getState();
			if (state == Thread.State.BLOCKED || state == Thread.State.WAITING
					|| state == Thread.State.TIMED_WAITING || state == Thread.State.TERMINATED) {
				return;
			}
			Thread.sleep(1);
		}
		throw new AssertionError("Invalidation neither blocked nor finished");
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
