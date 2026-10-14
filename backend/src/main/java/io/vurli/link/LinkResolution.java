package io.vurli.link;

/**
 * What a visitor of a short link should get.
 */
public sealed interface LinkResolution {

	/** The link is live: send the visitor to the stored target. */
	record Redirect(String targetUrl) implements LinkResolution {
	}

	/** The link existed but no longer redirects: disabled, blocked, deleted or expired. */
	record Gone() implements LinkResolution {
	}

	/** No link has this code. */
	record NotFound() implements LinkResolution {
	}

}
