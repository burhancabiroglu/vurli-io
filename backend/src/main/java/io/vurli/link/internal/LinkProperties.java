package io.vurli.link.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param shortDomain the domain short links are served from; links pointing back to it
 * are rejected
 * @param shortBaseUrl what a short link starts with, e.g. {@code https://vurli.io}; shown
 * in the panel and encoded in QR codes
 */
@ConfigurationProperties("vurli")
public record LinkProperties(@DefaultValue("vurli.io") String shortDomain,
		@DefaultValue("https://vurli.io") String shortBaseUrl) {

	public String shortUrl(String code) {
		return this.shortBaseUrl.endsWith("/") ? this.shortBaseUrl + code : this.shortBaseUrl + "/" + code;
	}

}
