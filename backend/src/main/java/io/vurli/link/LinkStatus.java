package io.vurli.link;

public enum LinkStatus {

	/** Redirects, unless expired or deleted. */
	ACTIVE,

	/** Turned off by its owner; the owner can turn it back on. */
	DISABLED,

	/** Blocked by an administrator; the owner cannot lift it. */
	BLOCKED

}
