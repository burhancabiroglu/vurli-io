package io.vurli.link;

/**
 * Whether a target is known to be dangerous (K24). The abuse module provides it with
 * Google Web Risk; links only ask, so they do not depend on how it is answered.
 */
public interface TargetSafety {

	enum Verdict {

		/** Not on any list right now. */
		SAFE,

		/** Phishing, malware or unwanted software: the link must not redirect there. */
		UNSAFE,

		/** No answer: the check is off or the service failed. Checked again later. */
		UNKNOWN

	}

	Verdict check(String targetUrl);

}
