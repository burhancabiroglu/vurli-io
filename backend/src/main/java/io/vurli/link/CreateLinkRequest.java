package io.vurli.link;

import java.time.Instant;

/**
 * @param targetUrl where the short link should redirect to
 * @param alias optional custom code; a random code is generated when it is {@code null} or blank
 * @param expiresAt optional moment after which the link stops redirecting
 */
public record CreateLinkRequest(String targetUrl, String alias, Instant expiresAt) {

	public static CreateLinkRequest of(String targetUrl) {
		return new CreateLinkRequest(targetUrl, null, null);
	}

}
