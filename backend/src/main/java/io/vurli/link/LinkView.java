package io.vurli.link;

import java.time.Instant;
import java.util.UUID;

public record LinkView(UUID id, String code, String targetUrl, LinkStatus status, Instant expiresAt,
		Instant createdAt, long version) {
}
