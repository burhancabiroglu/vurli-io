package io.vurli.link.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param shortDomain the domain short links are served from; links pointing back to it
 * are rejected
 */
@ConfigurationProperties("vurli")
public record LinkProperties(@DefaultValue("vurli.io") String shortDomain) {
}
