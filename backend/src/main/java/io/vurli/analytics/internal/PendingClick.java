package io.vurli.analytics.internal;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

/** A counted visit waiting in memory to be written; the address dies with it. */
public record PendingClick(UUID linkId, Instant occurredAt, String userAgent, String referer, InetAddress address) {
}
