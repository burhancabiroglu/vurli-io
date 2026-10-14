package io.vurli.link.internal;

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

}
