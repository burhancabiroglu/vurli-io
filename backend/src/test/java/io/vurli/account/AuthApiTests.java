package io.vurli.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.vurli.ClientHeaderFilter;
import io.vurli.Mailpit;
import io.vurli.TestAccounts;
import io.vurli.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AccountManagement accounts;

	@Autowired
	private Mailpit mailpit;

	@Test
	void registrationAnswersTheSameForNewAndExistingAddresses() throws Exception {
		String existing = TestAccounts.registerVerified(this.accounts, this.mailpit);
		String fresh = TestAccounts.uniqueEmail();

		String forFresh = this.mvc.perform(json(post("/api/v1/auth/register"), credentials(fresh)))
			.andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String forExisting = this.mvc.perform(json(post("/api/v1/auth/register"), credentials(existing)))
			.andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(forExisting).isEqualTo(forFresh);
	}

	@Test
	void loginStartsAHardenedSession() throws Exception {
		String email = TestAccounts.registerVerified(this.accounts, this.mailpit);

		var result = this.mvc.perform(json(post("/api/v1/auth/login"), credentials(email)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.emailVerified").value(true))
			.andReturn();

		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith(TestAccounts.SESSION_COOKIE + "=")
			.contains("HttpOnly")
			.contains("SameSite=Lax")
			.contains("Secure");
		this.mvc.perform(get("/api/v1/auth/me").cookie(result.getResponse().getCookie(TestAccounts.SESSION_COOKIE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void wrongPasswordAndUnknownAddressFailIdentically() throws Exception {
		String email = TestAccounts.registerVerified(this.accounts, this.mailpit);

		String wrongPassword = this.mvc
			.perform(json(post("/api/v1/auth/login"),
					"{\"email\": \"%s\", \"password\": \"not the password\"}".formatted(email)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("invalid_credentials"))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String unknownAddress = this.mvc
			.perform(json(post("/api/v1/auth/login"), credentials(TestAccounts.uniqueEmail())))
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(unknownAddress).isEqualTo(wrongPassword);
	}

	@Test
	void unverifiedAccountsCanSignIn() throws Exception {
		String email = TestAccounts.registerUnverified(this.accounts);

		Cookie session = TestAccounts.login(this.mvc, email, TestAccounts.PASSWORD);

		this.mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(jsonPath("$.emailVerified").value(false));
	}

	@Test
	void signingInAgainReplacesTheSessionId() throws Exception {
		String email = TestAccounts.registerVerified(this.accounts, this.mailpit);
		Cookie first = TestAccounts.login(this.mvc, email, TestAccounts.PASSWORD);

		Cookie second = this.mvc.perform(json(post("/api/v1/auth/login"), credentials(email)).cookie(first))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie(TestAccounts.SESSION_COOKIE);

		assertThat(second.getValue()).isNotEqualTo(first.getValue());
		this.mvc.perform(get("/api/v1/auth/me").cookie(first)).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/v1/auth/me").cookie(second)).andExpect(status().isOk());
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		String email = TestAccounts.registerVerified(this.accounts, this.mailpit);
		Cookie session = TestAccounts.login(this.mvc, email, TestAccounts.PASSWORD);

		this.mvc.perform(post("/api/v1/auth/logout").header(ClientHeaderFilter.HEADER, "test").cookie(session))
			.andExpect(status().isNoContent());

		this.mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void passwordResetSignsOutEverywhereAndReplacesThePassword() throws Exception {
		String email = TestAccounts.registerVerified(this.accounts, this.mailpit);
		Cookie session = TestAccounts.login(this.mvc, email, TestAccounts.PASSWORD);

		this.mvc.perform(json(post("/api/v1/auth/password-reset"), "{\"email\": \"%s\"}".formatted(email)))
			.andExpect(status().isAccepted());
		String token = this.mailpit.latestTo(email).token().orElseThrow();
		this.mvc
			.perform(json(post("/api/v1/auth/password-reset/confirm"),
					"{\"token\": \"%s\", \"password\": \"a brand new password\"}".formatted(token)))
			.andExpect(status().isNoContent());

		this.mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
		this.mvc.perform(json(post("/api/v1/auth/login"), credentials(email))).andExpect(status().isUnauthorized());
		TestAccounts.login(this.mvc, email, "a brand new password");
	}

	@Test
	void resetRequestsForUnknownAddressesLookTheSame() throws Exception {
		this.mvc
			.perform(json(post("/api/v1/auth/password-reset"),
					"{\"email\": \"%s\"}".formatted(TestAccounts.uniqueEmail())))
			.andExpect(status().isAccepted());
	}

	@Test
	void apiAnswers401InsteadOfRedirecting() throws Exception {
		this.mvc.perform(get("/api/v1/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist(HttpHeaders.LOCATION));
	}

	@Test
	void stateChangingRequestsWithoutClientHeaderAreRejected() throws Exception {
		this.mvc
			.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(credentials(TestAccounts.uniqueEmail())))
			.andExpect(status().isForbidden());
	}

	@Test
	void invalidInputHasStableCodes() throws Exception {
		this.mvc
			.perform(json(post("/api/v1/auth/register"),
					"{\"email\": \"%s\", \"password\": \"short\"}".formatted(TestAccounts.uniqueEmail())))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("weak_password"))
			.andExpect(jsonPath("$.reason").value("TOO_SHORT"));
		this.mvc.perform(json(post("/api/v1/auth/verify-email"), "{\"token\": \"nope\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_token"));
	}

	private static String credentials(String email) {
		return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, TestAccounts.PASSWORD);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.header(ClientHeaderFilter.HEADER, "test").contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
