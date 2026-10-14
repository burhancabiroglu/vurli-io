package io.vurli.link.internal;

import java.time.Instant;

import io.vurli.link.LinkStatus;

/**
 * The fields needed to decide whether a link redirects. Cached as is; the decision itself
 * is made on every request because expiry depends on the current time.
 */
public record LinkSnapshot(String targetUrl, LinkStatus status, Instant expiresAt, Instant deletedAt) {
}
