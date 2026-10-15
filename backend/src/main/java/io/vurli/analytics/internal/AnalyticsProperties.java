package io.vurli.analytics.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param queueCapacity clicks held in memory before new ones are dropped
 * @param retention how long raw clicks are kept; daily summaries are kept forever (K22)
 * @param geoipDatabase path to the country database (K44); empty means no countries
 */
@ConfigurationProperties("vurli.analytics")
public record AnalyticsProperties(@DefaultValue("10000") int queueCapacity, @DefaultValue("90d") Duration retention,
		@DefaultValue("") String geoipDatabase) {
}
