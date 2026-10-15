package io.vurli.abuse.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param apiKey Google Cloud API key restricted to the Web Risk API; empty turns the
 * check off and links stay unchecked until a key is set (K24, M4)
 * @param baseUrl the service, configurable for tests
 * @param timeout per lookup; a slow answer counts as no answer
 * @param recheckAfter how often a live link is looked up again
 * @param scanBatch links looked up per scan run; 100,000 lookups a month are free
 */
@ConfigurationProperties("vurli.web-risk")
public record WebRiskProperties(@DefaultValue("") String apiKey,
		@DefaultValue("https://webrisk.googleapis.com") String baseUrl, @DefaultValue("3s") Duration timeout,
		@DefaultValue("7d") Duration recheckAfter, @DefaultValue("50") int scanBatch) {

	public boolean enabled() {
		return !this.apiKey.isBlank();
	}

}
