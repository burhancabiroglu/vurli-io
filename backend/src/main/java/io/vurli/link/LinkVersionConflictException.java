package io.vurli.link;

/**
 * The link changed since the caller read it. The caller should reload it and decide
 * again instead of overwriting someone else's change.
 */
public class LinkVersionConflictException extends RuntimeException {

	public LinkVersionConflictException() {
		super("Link was changed by someone else");
	}

}
