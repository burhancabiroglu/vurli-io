package io.vurli.abuse;

import java.time.Instant;
import java.util.UUID;

/**
 * An open report as administrators see it.
 * @param linkId the reported link, or {@code null} if no link has the code
 * @param reporterEmail optional, may be {@code null}
 */
public record AbuseReport(UUID id, String code, UUID linkId, ReportReason reason, String details,
		String reporterEmail, Instant createdAt) {
}
