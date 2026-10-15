package io.vurli.abuse.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param trustForwardedFor whether the last {@code X-Forwarded-For} entry is the client.
 * True only when every request reaches Spring through a proxy that sets it: Caddy for
 * short links (it replaces whatever the visitor sent) and the Next.js panel for the API
 * on the internal network.
 */
@ConfigurationProperties("vurli.client-address")
public record ClientAddressProperties(@DefaultValue("false") boolean trustForwardedFor) {
}
