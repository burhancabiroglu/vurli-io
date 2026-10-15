package io.vurli.loadtest;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/**
 * Short link visits against the load test environment (loadtest/run.sh). Settings come
 * from environment variables, so run.sh can step through rates:
 * <ul>
 * <li>{@code SCENARIO}: {@code browse} (many links, mostly popular ones), {@code viral}
 * (one link) or {@code limit} (one visitor address, to see the rate limit act; run it at
 * 20 a second for 60 seconds)</li>
 * <li>{@code RATE}: visits per second, {@code DURATION}: seconds at that rate</li>
 * </ul>
 * Every visit carries its own visitor address in X-Forwarded-For, as Caddy would write
 * it, so the per-address limit (K42) applies as in production without stopping the test.
 */
public class RedirectSimulation extends Simulation {

	private static final String BASE_URL = env("BASE_URL", "http://localhost:18080");

	private static final String SCENARIO = env("SCENARIO", "browse");

	private static final int RATE = Integer.parseInt(env("RATE", "200"));

	private static final int DURATION = Integer.parseInt(env("DURATION", "60"));

	private static final int LINKS = 10_000;

	private static final String BROWSER = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) "
			+ "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1";

	/**
	 * In production visitors do not connect to the application: Caddy does, over a small
	 * pool of kept-alive connections. Sharing one pool across virtual users models that.
	 * A new connection per visit would instead measure the test machine's port reuse: on
	 * macOS with Docker Desktop the first run ran out of ports after about 11,000 visits.
	 */
	private final HttpProtocolBuilder protocol = http.baseUrl(BASE_URL)
		.shareConnections()
		.maxConnectionsPerHost(100)
		.disableFollowRedirect()
		.disableCaching()
		// The short link domain: HostFilter answers short links only there.
		.header("Host", "vurli.io")
		.userAgentHeader(BROWSER)
		.header("Referer", "https://www.instagram.com/");

	{
		ScenarioBuilder visits = switch (SCENARIO) {
			case "viral" -> scenario("viral").feed(visitors(() -> "viral"))
				.exec(http("redirect").get("/#{code}")
					.header("X-Forwarded-For", "#{ip}")
					.check(status().is(302), header("Location").is("https://example.com/viral")));
			// 429 counts as failed here, so the assertion below can check the limit acted.
			case "limit" -> scenario("limit").feed(oneVisitor())
				.exec(http("redirect").get("/#{code}").header("X-Forwarded-For", "#{ip}").check(status().is(302)));
			default -> scenario("browse").feed(visitors(RedirectSimulation::popularFirst))
				.exec(http("redirect").get("/#{code}")
					.header("X-Forwarded-For", "#{ip}")
					.check(status().is(302), header("Location").exists()));
		};

		setUp(visits.injectOpen(rampUsersPerSec(1).to(RATE).during(Duration.ofSeconds(10)),
				constantUsersPerSec(RATE).during(Duration.ofSeconds(DURATION))))
			.protocols(this.protocol)
			.assertions(SCENARIO.equals("limit") ? limitActed() : global().failedRequests().percent().lt(1.0));
	}

	/**
	 * One address at {@code RATE} visits per second against 300 a minute (K42): the bucket
	 * of 300 refills 5 a second, so at 20 a second it is empty after about 20 seconds and
	 * from then on only 5 of every 20 visits pass. Over a 60 second run roughly half are
	 * refused; the bounds leave room for timing.
	 */
	private static Assertion limitActed() {
		return global().failedRequests().percent().between(30.0, 75.0);
	}

	/**
	 * Like real traffic, a few links get most visits: 80% go to the first 200 links, the
	 * rest spread over all 10,000.
	 */
	private static String popularFirst() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int link = (random.nextInt(100) < 80) ? random.nextInt(200) : random.nextInt(LINKS);
		return "lt%05d".formatted(link);
	}

	private static Iterator<Map<String, Object>> visitors(Supplier<String> code) {
		return Stream.generate(() -> Map.<String, Object>of("code", code.get(), "ip", randomAddress())).iterator();
	}

	private static Iterator<Map<String, Object>> oneVisitor() {
		return Stream.generate(() -> Map.<String, Object>of("code", popularFirst(), "ip", "203.0.113.7")).iterator();
	}

	/** Public-looking IPv4 addresses from 11.0.0.0 to 99.255.255.255. */
	private static String randomAddress() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return random.nextInt(11, 100) + "." + random.nextInt(256) + "." + random.nextInt(256) + "."
				+ random.nextInt(1, 255);
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return (value == null || value.isBlank()) ? fallback : value;
	}

}
