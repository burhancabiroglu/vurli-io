package io.vurli.abuse;

import java.time.Instant;
import java.util.UUID;

/**
 * One line of the audit log.
 * @param actorId the administrator, or {@code null} for the system
 * @param details JSON object
 */
public record AuditEntry(long id, UUID actorId, String action, String targetType, String targetId, String details,
		Instant createdAt) {
}
