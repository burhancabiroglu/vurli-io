package io.vurli.link.internal;

import java.time.Instant;
import java.util.UUID;

import io.vurli.link.LinkStatus;

/**
 * The fields needed to decide whether a link redirects. Cached as is; the decision itself
 * is made on every request because expiry depends on the current time.
 */
public record LinkSnapshot(UUID linkId, String targetUrl, LinkStatus status, Instant expiresAt, Instant deletedAt) {
}
