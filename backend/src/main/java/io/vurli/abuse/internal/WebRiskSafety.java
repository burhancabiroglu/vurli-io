package io.vurli.abuse.internal;

import java.net.http.HttpClient;
import java.util.Locale;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import io.vurli.link.TargetSafety;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Google Web Risk Lookup API ({@code uris.search}, K24). A response with a {@code threat}
 * means the URL is listed. The key travels in a header, never in a URL, and target URLs
 * are never logged: their query strings may hold tokens.
 */
public class WebRiskSafety implements TargetSafety {

	private static final Logger log = LoggerFactory.getLogger(WebRiskSafety.class);

	private final WebRiskProperties properties;

	private final RestClient client;

	private final MeterRegistry meters;

	public WebRiskSafety(WebRiskProperties properties, MeterRegistry meters) {
		this.properties = properties;
		this.meters = meters;
		HttpClient http = HttpClient.newBuilder()
			.connectTimeout(properties.timeout())
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
		requests.setReadTimeout(properties.timeout());
		this.client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requests).build();
	}

	@Override
	public Verdict check(String targetUrl) {
		if (!this.properties.enabled()) {
			return count(Verdict.UNKNOWN);
		}
		try {
			Map<?, ?> body = this.client.get()
				.uri((uri) -> uri.path("/v1/uris:search")
					.queryParam("threatTypes", "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE")
					.queryParam("uri", "{uri}")
					.build(Map.of("uri", targetUrl)))
				.header("X-goog-api-key", this.properties.apiKey())
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(Map.class);
			return count((body != null && body.get("threat") != null) ? Verdict.UNSAFE : Verdict.SAFE);
		}
		catch (RestClientException ex) {
			log.warn("Web Risk lookup failed, the link stays unchecked: {}", ex.getClass().getSimpleName());
			return count(Verdict.UNKNOWN);
		}
	}

	private Verdict count(Verdict verdict) {
		this.meters.counter("vurli.safety.checks", "verdict", verdict.name().toLowerCase(Locale.ROOT))
			.increment();
		return verdict;
	}

}
