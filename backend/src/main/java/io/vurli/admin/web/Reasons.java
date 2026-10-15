package io.vurli.admin.web;

/**
 * Every block, unblock and suspension needs a reason (K50); it goes into the audit log so
 * a later reader knows why.
 */
final class Reasons {

	static final int MIN = 3;

	static final int MAX = 500;

	private Reasons() {
	}

	/** @return the trimmed reason, or {@code null} if it is missing, too short or too long */
	static String valid(String reason) {
		String text = (reason == null) ? "" : reason.strip();
		return (text.length() >= MIN && text.length() <= MAX) ? text : null;
	}

}
