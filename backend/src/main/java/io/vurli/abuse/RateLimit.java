package io.vurli.abuse;

/**
 * The limits of K42. Capacities and periods come from {@code vurli.rate-limits.*}.
 */
public enum RateLimit {

	/** Login attempts from one address. */
	LOGIN_PER_IP,

	/**
	 * Failed logins for one email address, from anywhere. Keyed by the address as typed,
	 * whether or not it has an account, so the limit reveals nothing.
	 */
	LOGIN_FAILURES_PER_ACCOUNT,

	/** Requests that send an email (registration, password reset, verification) from one address. */
	EMAILS_PER_IP,

	/** Link creations by one user; the daily quota is counted separately in the database. */
	LINK_CREATIONS_PER_USER,

	/** Short link visits from one address. */
	REDIRECTS_PER_IP,

	/** Wrong second-factor codes of one administrator, wherever they come from. */
	TOTP_FAILURES_PER_USER

}
