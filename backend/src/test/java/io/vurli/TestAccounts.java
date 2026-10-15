package io.vurli;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import io.vurli.account.AccountManagement;
import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Creates accounts the way a user would: register, read the email, follow the link.
 */
public final class TestAccounts {

	public static final String PASSWORD = "correct horse battery";

	public static final String SESSION_COOKIE = "VURLI_SESSION";

	private TestAccounts() {
	}

	/** An address from 10.0.0.0/8, distinct enough that tests do not share rate limits. */
	public static String randomAddress() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
	}

	public static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	public static String registerVerified(AccountManagement accounts, Mailpit mailpit) {
		String email = uniqueEmail();
		accounts.register(email, PASSWORD, Locale.ENGLISH);
		accounts.verifyEmail(mailpit.latestTo(email).token().orElseThrow());
		return email;
	}

	public static String registerUnverified(AccountManagement accounts) {
		String email = uniqueEmail();
		accounts.register(email, PASSWORD, Locale.ENGLISH);
		return email;
	}

	/** Signs in through the API and returns the session cookie. */
	public static Cookie login(MockMvc mvc, String email, String password) throws Exception {
		MvcResult result = mvc
			.perform(post("/api/v1/auth/login").header(ClientHeaderFilter.HEADER, "test")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)))
			.andReturn();
		Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
		if (result.getResponse().getStatus() != 200 || cookie == null) {
			throw new AssertionError("Login failed with status " + result.getResponse().getStatus());
		}
		return cookie;
	}

}
