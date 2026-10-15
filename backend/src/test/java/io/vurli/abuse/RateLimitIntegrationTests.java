package io.vurli.abuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import io.vurli.ClientHeaderFilter;
import io.vurli.Mailpit;
import io.vurli.TestAccounts;
import io.vurli.TestcontainersConfiguration;
import io.vurli.account.AccountManagement;
import io.vurli.link.CreateLinkRequest;
import io.vurli.link.LinkManagement;
import io.vurli.link.LinkView;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The limits of K42 against the real API. Each test uses its own addresses and accounts,
 * because buckets live as long as the application context.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AccountManagement accounts;

	@Autowired
	private LinkManagement links;

	@Autowired
	private Mailpit mailpit;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void loginAttemptsAreLimitedPerAddress() throws Exception {
		String address = TestAccounts.randomAddress();
		for (int i = 0; i < 10; i++) {
			login(address, TestAccounts.uniqueEmail(), "wrong password").andExpect(status().isUnauthorized());
		}

		login(address, TestAccounts.uniqueEmail(), "wrong password").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("rate_limited"))
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "6"));
		login(TestAccounts.randomAddress(), TestAccounts.uniqueEmail(), "wrong password")
			.andExpect(status().isUnauthorized());
	}

	@Test
	void failedLoginsAreLimitedPerAccountFromAnyAddress() throws Exception {
		String email = TestAccounts.registerVerified(this.accounts, this.mailpit);
		for (int i = 0; i < 10; i++) {
			login(TestAccounts.randomAddress(), email, "wrong password").andExpect(status().isUnauthorized());
		}

		// Even the right password: guessing is stopped before the password is checked.
		login(TestAccounts.randomAddress(), email.toUpperCase(), TestAccounts.PASSWORD)
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("rate_limited"))
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));

		String other = TestAccounts.registerVerified(this.accounts, this.mailpit);
		login(TestAccounts.randomAddress(), other, TestAccounts.PASSWORD).andExpect(status().isOk());
	}

	@Test
	void unknownAddressesAreLimitedTheSameWay() throws Exception {
		String unknown = TestAccounts.uniqueEmail();
		for (int i = 0; i < 10; i++) {
			login(TestAccounts.randomAddress(), unknown, "wrong password").andExpect(status().isUnauthorized());
		}

		login(TestAccounts.randomAddress(), unknown, "wrong password").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("rate_limited"));
	}

	@Test
	void successfulLoginsAreNotFailures() throws Exception {
		String email = TestAccounts.registerVerified(this.accounts, this.mailpit);

		for (int i = 0; i < 12; i++) {
			login(TestAccounts.randomAddress(), email, TestAccounts.PASSWORD).andExpect(status().isOk());
		}
	}

	@Test
	void requestsThatSendEmailShareOneLimitPerAddress() throws Exception {
		String address = TestAccounts.randomAddress();
		for (int i = 0; i < 3; i++) {
			register(address, TestAccounts.uniqueEmail()).andExpect(status().isAccepted());
		}
		for (int i = 0; i < 2; i++) {
			this.mvc.perform(json(post("/api/v1/auth/password-reset"), address,
					"{\"email\": \"%s\"}".formatted(TestAccounts.uniqueEmail())))
				.andExpect(status().isAccepted());
		}

		String blocked = TestAccounts.uniqueEmail();
		register(address, blocked).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("rate_limited"))
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
		assertThat(this.mailpit.messagesTo(blocked)).isEmpty();
		assertThat(this.jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, blocked)).isZero();

		register(TestAccounts.randomAddress(), TestAccounts.uniqueEmail()).andExpect(status().isAccepted());
	}

	@Test
	void linkCreationIsLimitedPerUser() throws Exception {
		Cookie user = verifiedSession();
		for (int i = 0; i < 10; i++) {
			createLink(user).andExpect(status().isCreated());
		}

		createLink(user).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("rate_limited"))
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
		createLink(verifiedSession()).andExpect(status().isCreated());
	}

	@Test
	void dailyQuotaCountsDeletedLinksAndRollsOver() throws Exception {
		Cookie user = verifiedSession();
		UUID workspaceId = UUID.fromString(JsonPath.read(
				this.mvc.perform(get("/api/v1/auth/me").cookie(user)).andReturn().getResponse().getContentAsString(),
				"$.workspaceId"));
		for (int i = 0; i < 49; i++) {
			LinkView link = this.links.create(workspaceId, CreateLinkRequest.of("https://example.com/" + i));
			if (i == 0) {
				this.links.delete(workspaceId, link.id(), link.version());
			}
		}

		createLink(user).andExpect(status().isCreated());
		createLink(user).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("link_quota_exceeded"))
			.andExpect(jsonPath("$.quota").value(50));

		this.jdbc.update("update links set created_at = created_at - interval '25 hours' where workspace_id = ?",
				workspaceId);
		createLink(user).andExpect(status().isCreated());
	}

	@Test
	void redirectsAreLimitedPerAddress() throws Exception {
		Cookie user = verifiedSession();
		String code = JsonPath.read(createLink(user).andReturn().getResponse().getContentAsString(), "$.code");
		String address = TestAccounts.randomAddress();
		for (int i = 0; i < 300; i++) {
			this.mvc.perform(get("http://vurli.io/" + code).with(from(address))).andExpect(status().isFound());
		}
		// The bucket refills 5 tokens a second, so a slow machine earns a few extra visits
		// while the loop runs. Within a few more the limit must hold.
		int extra = 0;
		while (this.mvc.perform(get("http://vurli.io/" + code).with(from(address)))
			.andReturn()
			.getResponse()
			.getStatus() == 302) {
			assertThat(++extra).as("visits allowed beyond 300").isLessThan(50);
		}

		this.mvc.perform(get("http://vurli.io/" + code).with(from(address)))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().doesNotExist(HttpHeaders.LOCATION))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
		this.mvc.perform(get("http://vurli.io/" + code).with(from(TestAccounts.randomAddress())))
			.andExpect(status().isFound());
	}

	@Test
	void theForwardedAddressIsTheKey() throws Exception {
		// The panel calls the API from one address and passes on the visitor's.
		String panel = TestAccounts.randomAddress();
		String visitor = TestAccounts.randomAddress();
		for (int i = 0; i < 10; i++) {
			this.mvc.perform(json(post("/api/v1/auth/login"), panel,
					"{\"email\": \"%s\", \"password\": \"x\"}".formatted(TestAccounts.uniqueEmail()))
				.header("X-Forwarded-For", "198.51.100.1, " + visitor))
				.andExpect(status().isUnauthorized());
		}

		this.mvc.perform(json(post("/api/v1/auth/login"), panel,
				"{\"email\": \"%s\", \"password\": \"x\"}".formatted(TestAccounts.uniqueEmail()))
			.header("X-Forwarded-For", visitor)).andExpect(status().isTooManyRequests());
		this.mvc.perform(json(post("/api/v1/auth/login"), panel,
				"{\"email\": \"%s\", \"password\": \"x\"}".formatted(TestAccounts.uniqueEmail()))
			.header("X-Forwarded-For", TestAccounts.randomAddress())).andExpect(status().isUnauthorized());
	}

	private ResultActions login(String address, String email, String password) throws Exception {
		return this.mvc.perform(json(post("/api/v1/auth/login"), address,
				"{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
	}

	private ResultActions register(String address, String email) throws Exception {
		return this.mvc.perform(json(post("/api/v1/auth/register"), address,
				"{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, TestAccounts.PASSWORD)));
	}

	private ResultActions createLink(Cookie session) throws Exception {
		return this.mvc.perform(json(post("/api/v1/links"), TestAccounts.randomAddress(),
				"{\"targetUrl\": \"https://example.com/limits\"}").cookie(session));
	}

	private Cookie verifiedSession() throws Exception {
		return TestAccounts.login(this.mvc, TestAccounts.registerVerified(this.accounts, this.mailpit),
				TestAccounts.PASSWORD);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String address,
			String body) {
		return request.with(from(address))
			.header(ClientHeaderFilter.HEADER, "test")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	private static RequestPostProcessor from(String address) {
		return (request) -> {
			request.setRemoteAddr(address);
			return request;
		};
	}

}
