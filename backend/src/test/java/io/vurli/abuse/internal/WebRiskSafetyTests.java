package io.vurli.abuse.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vurli.link.TargetSafety.Verdict;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Against a local stand-in for webrisk.googleapis.com. */
class WebRiskSafetyTests {

	private HttpServer server;

	private final AtomicReference<Response> next = new AtomicReference<>(new Response(200, "{}", 0));

	private final List<HttpExchange> requests = new ArrayList<>();

	private final List<String> keys = new ArrayList<>();

	private record Response(int status, String body, long delayMillis) {
	}

	@BeforeEach
	void start() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/v1/uris:search", (exchange) -> {
			this.requests.add(exchange);
			this.keys.add(exchange.getRequestHeaders().getFirst("X-goog-api-key"));
			Response response = this.next.get();
			try {
				Thread.sleep(response.delayMillis());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(response.status(), body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		this.server.start();
	}

	@AfterEach
	void stop() {
		this.server.stop(0);
	}

	@Test
	void anEmptyAnswerMeansSafe() {
		assertThat(safety("key").check("https://example.com/")).isEqualTo(Verdict.SAFE);
	}

	@Test
	void aThreatMeansUnsafe() {
		this.next.set(new Response(200, """
				{"threat": {"threatTypes": ["SOCIAL_ENGINEERING"], "expireTime": "2026-10-16T10:00:00Z"}}""", 0));

		assertThat(safety("key").check("https://phish.example/")).isEqualTo(Verdict.UNSAFE);
	}

	@Test
	void sendsTheKeyInAHeaderAndTheTargetAsOneEncodedParameter() {
		String target = "https://example.com/menu?a=1&b=2&uri=https://evil.com";

		safety("secret-key").check(target);

		HttpExchange request = this.requests.get(0);
		String query = request.getRequestURI().getRawQuery();
		assertThat(this.keys).containsExactly("secret-key");
		assertThat(query).doesNotContain("secret-key").doesNotContain("key=");
		assertThat(query.split("&")).filteredOn((p) -> p.startsWith("uri="))
			.singleElement()
			.satisfies((p) -> assertThat(URLDecoder.decode(p.substring(4), StandardCharsets.UTF_8)).isEqualTo(target));
		assertThat(query).contains("threatTypes=MALWARE", "threatTypes=SOCIAL_ENGINEERING",
				"threatTypes=UNWANTED_SOFTWARE");
	}

	@Test
	void anErrorOrASlowAnswerMeansUnknown() {
		this.next.set(new Response(500, "{}", 0));
		assertThat(safety("key").check("https://example.com/")).isEqualTo(Verdict.UNKNOWN);

		this.next.set(new Response(403, "{\"error\": {\"message\": \"API key not valid\"}}", 0));
		assertThat(safety("key").check("https://example.com/")).isEqualTo(Verdict.UNKNOWN);

		this.next.set(new Response(200, "{}", 1500));
		assertThat(safety("key").check("https://example.com/")).isEqualTo(Verdict.UNKNOWN);
	}

	@Test
	void withoutAKeyNothingIsSent() {
		assertThat(safety("").check("https://example.com/")).isEqualTo(Verdict.UNKNOWN);
		assertThat(this.requests).isEmpty();
	}

	private WebRiskSafety safety(String key) {
		String baseUrl = "http://127.0.0.1:" + this.server.getAddress().getPort();
		return new WebRiskSafety(new WebRiskProperties(key, baseUrl, Duration.ofMillis(500), Duration.ofDays(7), 50),
				new SimpleMeterRegistry());
	}

}
