package io.vurli.link.internal;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Ticker;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LinkProperties.class)
class LinkConfiguration {

	@Bean
	TargetUrlValidator targetUrlValidator(LinkProperties properties) {
		return new TargetUrlValidator(properties.shortDomain());
	}

	@Bean
	CodeGenerator codeGenerator() {
		return new RandomCodeGenerator();
	}

	@Bean
	LinkCache linkCache(LinkRepository links) {
		return new LinkCache(links::findSnapshotByCode, Duration.ofSeconds(60), 100_000, Ticker.systemTicker());
	}

}
