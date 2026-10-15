package io.vurli.link;

import java.util.UUID;

/** A link whose target is due for a safety check. */
public record SafetyCandidate(UUID id, String code, String targetUrl) {
}
