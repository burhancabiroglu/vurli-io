package io.vurli.account;

import java.time.Instant;
import java.util.UUID;

/**
 * An account as administrators see it.
 * @param suspendedAt when it was suspended, or {@code null}
 * @param workspaceId its personal workspace
 */
public record AccountSummary(UUID id, String email, String role, boolean emailVerified, Instant suspendedAt,
		Instant createdAt, UUID workspaceId) {

	public boolean suspended() {
		return this.suspendedAt != null;
	}

	public boolean administrator() {
		return "ADMIN".equals(this.role);
	}

}
