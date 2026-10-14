package io.vurli.account;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in user as stored in the session. Holds only identifiers that never change
 * for an account; anything that can change (such as email verification) is read from
 * the database when it matters.
 */
public final class VurliUser implements UserDetails, CredentialsContainer {

	@Serial
	private static final long serialVersionUID = 1L;

	private final UUID userId;

	private final UUID workspaceId;

	private final String email;

	private final String role;

	private String passwordHash;

	public VurliUser(UUID userId, UUID workspaceId, String email, String role, String passwordHash) {
		this.userId = userId;
		this.workspaceId = workspaceId;
		this.email = email;
		this.role = role;
		this.passwordHash = passwordHash;
	}

	public UUID userId() {
		return this.userId;
	}

	/** The personal workspace every account gets on registration. */
	public UUID workspaceId() {
		return this.workspaceId;
	}

	/** Spring Security's principal name; also how sessions are found for revocation. */
	@Override
	public String getUsername() {
		return this.email;
	}

	@Override
	public String getPassword() {
		return this.passwordHash;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + this.role));
	}

	/** Called after authentication so the hash never ends up in the session. */
	@Override
	public void eraseCredentials() {
		this.passwordHash = null;
	}

}
