package io.vurli.analytics.internal;

import java.io.File;
import java.io.IOException;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AnalyticsProperties.class)
class AnalyticsConfiguration {

	private static final Logger log = LoggerFactory.getLogger(AnalyticsConfiguration.class);

	/**
	 * Without the database file clicks are still counted, just without a country; a
	 * missing download must never stop the application.
	 */
	@Bean
	CountryLookup countryLookup(AnalyticsProperties properties) {
		if (properties.geoipDatabase().isBlank()) {
			log.info("No country database configured; clicks are recorded without a country");
			return CountryLookup.NONE;
		}
		File file = new File(properties.geoipDatabase());
		try {
			MmdbCountryLookup lookup = new MmdbCountryLookup(file);
			log.info("Country database loaded: {} ({})", file, lookup.databaseType());
			return lookup;
		}
		catch (IOException ex) {
			log.warn("Country database {} not readable; clicks are recorded without a country "
					+ "(run scripts/download-geoip.sh): {}", file, ex.getMessage());
			return CountryLookup.NONE;
		}
	}

	@Bean
	ClickWriter clickWriter(AnalyticsProperties properties, JdbcTemplate jdbc, CountryLookup countryLookup,
			MeterRegistry meters) {
		return new ClickWriter(properties.queueCapacity(), jdbc, countryLookup, meters);
	}

}
