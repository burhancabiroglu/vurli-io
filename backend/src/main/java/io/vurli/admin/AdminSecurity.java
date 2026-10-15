package io.vurli.admin;

/**
 * Granted only after the second factor (K47). The admin panel requires it rather than
 * the ADMIN role: an administrator's panel session, signed in with the password alone,
 * carries ROLE_ADMIN too and must not open the admin panel.
 */
public final class AdminSecurity {

	public static final String SECOND_FACTOR = "ADMIN_SECOND_FACTOR";

	private AdminSecurity() {
	}

}
