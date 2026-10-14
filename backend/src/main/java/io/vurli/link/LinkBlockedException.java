package io.vurli.link;

/**
 * The link was blocked by an administrator; its owner can only delete it.
 */
public class LinkBlockedException extends RuntimeException {

	public LinkBlockedException() {
		super("Link is blocked");
	}

}
