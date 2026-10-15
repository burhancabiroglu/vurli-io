package io.vurli.account.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param totpKey Base64 of 32 random bytes that encrypts TOTP secrets (K47); required
 * @param promote email of a verified account to make an administrator at startup (K48)
 * @param resetTotp email of an administrator whose second factor is removed at startup,
 * for a lost phone and lost backup codes (K49); remove it again after use
 * @param sessionTimeout idle time after which an administrator must sign in again
 */
@ConfigurationProperties("vurli.admin")
public record AdminProperties(String totpKey, @DefaultValue("") String promote, @DefaultValue("") String resetTotp,
		@DefaultValue("1h") Duration sessionTimeout) {
}
