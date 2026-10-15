package io.vurli.analytics;

import java.net.InetAddress;
import java.util.UUID;

/**
 * A short link visit as the redirect saw it. The address is only used to find the
 * country and is never stored (K23).
 * @param userAgent the {@code User-Agent} header, may be {@code null}
 * @param referer the {@code Referer} header, may be {@code null}
 * @param address the visitor's address, may be {@code null}
 * @param prefetch whether the browser only prefetched the link
 */
public record Visit(UUID linkId, String userAgent, String referer, InetAddress address, boolean prefetch) {
}
