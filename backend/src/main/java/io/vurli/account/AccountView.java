package io.vurli.account;

import java.util.UUID;

public record AccountView(UUID id, String email, boolean emailVerified, String locale, UUID workspaceId) {
}
