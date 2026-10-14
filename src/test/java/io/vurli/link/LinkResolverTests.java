package io.vurli.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Ticker;
import io.vurli.link.internal.LinkCache;
import io.vurli.link.internal.LinkSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class LinkResolverTests {

	private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

	private final Map<String, LinkSnapshot> database = new HashMap<>();

	private final AtomicInteger lookups = new AtomicInteger();

	private final LinkResolver resolver = new LinkResolver(new LinkCache((code) -> {
		this.lookups.incrementAndGet();
		return Optional.ofNullable(this.database.get(code));
	}, Duration.ofMinutes(1), 100, Ticker.systemTicker()), Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void activeLinkRedirectsToStoredTarget() {
		store("kafe-menu", LinkStatus.ACTIVE, null, null);

		assertThat(this.resolver.resolve("kafe-menu"))
			.isEqualTo(new LinkResolution.Redirect("https://example.com/menu"));
	}

	@ParameterizedTest
	@EnumSource(value = LinkStatus.class, names = { "DISABLED", "BLOCKED" })
	void inactiveLinkIsGone(LinkStatus status) {
		store("kafe-menu", status, null, null);

		assertThat(this.resolver.resolve("kafe-menu")).isInstanceOf(LinkResolution.Gone.class);
	}

	@Test
	void deletedLinkIsGone() {
		store("kafe-menu", LinkStatus.ACTIVE, null, NOW.minusSeconds(1));

		assertThat(this.resolver.resolve("kafe-menu")).isInstanceOf(LinkResolution.Gone.class);
	}

	@Test
	void linkExpiresExactlyAtExpiry() {
		store("tam-sinir", LinkStatus.ACTIVE, NOW, null);
		store("son-anda", LinkStatus.ACTIVE, NOW.plus(Duration.ofMillis(1)), null);

		assertThat(this.resolver.resolve("tam-sinir")).isInstanceOf(LinkResolution.Gone.class);
		assertThat(this.resolver.resolve("son-anda")).isInstanceOf(LinkResolution.Redirect.class);
	}

	@Test
	void unknownCodeIsNotFound() {
		assertThat(this.resolver.resolve("bilinmiyor")).isInstanceOf(LinkResolution.NotFound.class);
	}

	@Test
	void codesAreCaseSensitive() {
		store("AbC1234", LinkStatus.ACTIVE, null, null);

		assertThat(this.resolver.resolve("abc1234")).isInstanceOf(LinkResolution.NotFound.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "ab", "a.b", "a/b", "kafe menu", "kafe_menu", "menü-1",
			"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "admin", "ADMIN", "actuator" })
	void impossibleOrReservedCodesAreNotFoundWithoutLookup(String code) {
		assertThat(this.resolver.resolve(code)).isInstanceOf(LinkResolution.NotFound.class);
		assertThat(this.lookups).hasValue(0);
	}

	@Test
	void nullCodeIsNotFound() {
		assertThat(this.resolver.resolve(null)).isInstanceOf(LinkResolution.NotFound.class);
	}

	private void store(String code, LinkStatus status, Instant expiresAt, Instant deletedAt) {
		this.database.put(code, new LinkSnapshot("https://example.com/menu", status, expiresAt, deletedAt));
	}

}
