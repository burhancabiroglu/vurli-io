package io.vurli.account.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserAccount {

	static final String ROLE_USER = "USER";

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private String email;

	@Column(nullable = false)
	private String passwordHash;

	private Instant emailVerifiedAt;

	@Column(nullable = false)
	private String locale;

	@Column(nullable = false)
	private String role;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected UserAccount() {
	}

	public static UserAccount register(String email, String passwordHash, String locale, Instant now) {
		UserAccount user = new UserAccount();
		user.id = UUID.randomUUID();
		user.email = email;
		user.passwordHash = passwordHash;
		user.locale = locale;
		user.role = ROLE_USER;
		user.createdAt = stored(now);
		user.updatedAt = user.createdAt;
		return user;
	}

	public UUID id() {
		return this.id;
	}

	public String email() {
		return this.email;
	}

	public String passwordHash() {
		return this.passwordHash;
	}

	public String locale() {
		return this.locale;
	}

	public String role() {
		return this.role;
	}

	public boolean isEmailVerified() {
		return this.emailVerifiedAt != null;
	}

	public void markEmailVerified(Instant now) {
		if (this.emailVerifiedAt == null) {
			this.emailVerifiedAt = stored(now);
			this.updatedAt = this.emailVerifiedAt;
		}
	}

	public void changePassword(String passwordHash, Instant now) {
		this.passwordHash = passwordHash;
		this.updatedAt = stored(now);
	}

	/** PostgreSQL keeps microseconds; see the same rule in the link module. */
	private static Instant stored(Instant instant) {
		return instant.truncatedTo(ChronoUnit.MICROS);
	}

}
