package io.vurli.account.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param appBaseUrl where the user panel runs; links in emails point there
 * @param mail sender of account emails
 */
@ConfigurationProperties("vurli")
public record AccountProperties(String appBaseUrl, Mail mail) {

	public record Mail(String from) {
	}

}
