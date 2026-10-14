package io.vurli.link;

/**
 * No link with this id exists in the caller's workspace. Links of other workspaces and
 * deleted links look exactly the same, so their existence is never revealed.
 */
public class LinkNotFoundException extends RuntimeException {

	public LinkNotFoundException() {
		super("Link not found");
	}

}
