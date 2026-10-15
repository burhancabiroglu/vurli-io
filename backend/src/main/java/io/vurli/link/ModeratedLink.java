package io.vurli.link;

import java.time.Instant;
import java.util.UUID;

/**
 * A link as administrators see it: from any workspace, deleted ones included.
 * @param deletedAt when the owner deleted it, or {@code null}
 */
public record ModeratedLink(UUID id, String code, String targetUrl, LinkStatus status, UUID workspaceId,
		Instant createdAt, Instant deletedAt) {
}
