package io.vurli.ratelimit.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ RateLimitProperties.class, ClientAddressProperties.class })
class RateLimitConfiguration {

}
