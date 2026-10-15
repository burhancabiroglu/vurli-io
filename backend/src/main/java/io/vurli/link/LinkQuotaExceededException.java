package io.vurli.link;

/**
 * The workspace created as many links in the last 24 hours as its quota allows (K42).
 * Deleted links still count, so deleting does not buy new ones.
 */
public class LinkQuotaExceededException extends RuntimeException {

	private final int quota;

	public LinkQuotaExceededException(int quota) {
		super("Daily link quota reached: " + quota);
		this.quota = quota;
	}

	public int quota() {
		return this.quota;
	}

}
