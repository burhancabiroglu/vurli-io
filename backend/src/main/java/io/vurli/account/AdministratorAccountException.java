package io.vurli.account;

/** Administrators cannot be suspended; take the role away first (K50). */
public class AdministratorAccountException extends RuntimeException {

	public AdministratorAccountException() {
		super("Administrator accounts cannot be suspended");
	}

}
