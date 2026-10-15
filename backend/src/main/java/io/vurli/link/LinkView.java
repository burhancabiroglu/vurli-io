package io.vurli.link;

import java.time.Instant;
import java.util.UUID;

/**
 * @param shortUrl the full short link, e.g. {@code https://vurli.io/kafe-menu}
 */
public record LinkView(UUID id, String code, String shortUrl, String targetUrl, LinkStatus status,
		Instant expiresAt, Instant createdAt, long version) {
}
