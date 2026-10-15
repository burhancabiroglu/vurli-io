package io.vurli;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HostConfiguration.HostProperties.class)
class HostConfiguration {

	/** First filter of all: a request for a foreign host should not even load a session. */
	@Bean
	FilterRegistrationBean<HostFilter> hostFilter(HostProperties hosts) {
		FilterRegistrationBean<HostFilter> registration = new FilterRegistrationBean<>(
				new HostFilter(hosts.publicHosts(), hosts.internalHosts()));
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

	/**
	 * @param publicHosts hosts that serve short links, e.g. {@code vurli.io}
	 * @param internalHosts hosts the API and actuator answer on: the name the Next.js
	 * panel and the health check use inside the private network
	 */
	@ConfigurationProperties("vurli.hosts")
	record HostProperties(@Name("public") List<String> publicHosts, @Name("internal") List<String> internalHosts) {
	}

}
