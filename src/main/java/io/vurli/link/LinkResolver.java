package io.vurli.link;

import java.time.Clock;
import java.util.regex.Pattern;

import io.vurli.link.internal.AliasPolicy;
import io.vurli.link.internal.LinkCache;
import io.vurli.link.internal.LinkSnapshot;

import org.springframework.stereotype.Service;

/**
 * Decides where a short code leads. Used on every redirect, so it reads through a cache
 * and never touches management data.
 */
@Service
public class LinkResolver {

	/** Anything that cannot be a code is answered without a cache or database lookup. */
	private static final Pattern CODE = Pattern.compile("[0-9A-Za-z-]{3,50}");

	private static final LinkResolution GONE = new LinkResolution.Gone();

	private static final LinkResolution NOT_FOUND = new LinkResolution.NotFound();

	private final LinkCache cache;

	private final Clock clock;

	public LinkResolver(LinkCache cache, Clock clock) {
		this.cache = cache;
		this.clock = clock;
	}

	public LinkResolution resolve(String code) {
		if (code == null || !CODE.matcher(code).matches() || AliasPolicy.isReserved(code)) {
			return NOT_FOUND;
		}
		return this.cache.get(code).map(this::decide).orElse(NOT_FOUND);
	}

	private LinkResolution decide(LinkSnapshot link) {
		if (link.deletedAt() != null || link.status() != LinkStatus.ACTIVE) {
			return GONE;
		}
		// Checked here on every request, so expiry never depends on a scheduled job.
		if (link.expiresAt() != null && !this.clock.instant().isBefore(link.expiresAt())) {
			return GONE;
		}
		return new LinkResolution.Redirect(link.targetUrl());
	}

}
