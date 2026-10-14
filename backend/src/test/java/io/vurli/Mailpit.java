package io.vurli;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.client.RestClient;

/**
 * Reads emails caught by the Mailpit test container.
 */
public class Mailpit {

	private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]{43})");

	private final Supplier<String> baseUrl;

	public Mailpit(Supplier<String> baseUrl) {
		this.baseUrl = baseUrl;
	}

	/** All messages sent to the address, newest first. */
	@SuppressWarnings("unchecked")
	public List<Email> messagesTo(String address) {
		Map<String, Object> result = client().get()
			.uri("/api/v1/search?query={query}", "to:\"" + address + "\"")
			.retrieve()
			.body(Map.class);
		List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get("messages");
		return messages.stream().map((summary) -> read((String) summary.get("ID"))).toList();
	}

	/** Waits briefly for the newest message to the address. */
	public Email latestTo(String address) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline) {
			List<Email> messages = messagesTo(address);
			if (!messages.isEmpty()) {
				return messages.getFirst();
			}
			sleep();
		}
		throw new AssertionError("No email to " + address);
	}

	@SuppressWarnings("unchecked")
	private Email read(String id) {
		Map<String, Object> message = client().get().uri("/api/v1/message/{id}", id).retrieve().body(Map.class);
		Map<String, Object> from = (Map<String, Object>) message.get("From");
		return new Email((String) message.get("Subject"), (String) message.get("Text"), (String) from.get("Address"));
	}

	private RestClient client() {
		return RestClient.create(this.baseUrl.get());
	}

	private static void sleep() {
		try {
			Thread.sleep(50);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	public record Email(String subject, String text, String from) {

		/** The token from a {@code #token=} link in the body. */
		public Optional<String> token() {
			Matcher matcher = TOKEN.matcher(this.text);
			return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
		}

	}

}
