package io.vurli.abuse.internal;

import java.time.Clock;

import io.micrometer.core.instrument.MeterRegistry;
import io.vurli.audit.AuditLog;
import io.vurli.link.LinkModeration;
import io.vurli.link.TargetSafety;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebRiskProperties.class)
class AbuseConfiguration {

	private static final Logger log = LoggerFactory.getLogger(AbuseConfiguration.class);

	@Bean
	TargetSafety targetSafety(WebRiskProperties properties, MeterRegistry meters) {
		if (!properties.enabled()) {
			log.warn("No Web Risk API key (vurli.web-risk.api-key): targets are not checked and links stay "
					+ "unchecked until a key is set");
		}
		return new WebRiskSafety(properties, meters);
	}

	@Bean
	SafetyScanner safetyScanner(LinkModeration moderation, TargetSafety safety, AuditLog audit,
			WebRiskProperties properties, Clock clock) {
		return new SafetyScanner(moderation, safety, audit, properties, clock);
	}

}
