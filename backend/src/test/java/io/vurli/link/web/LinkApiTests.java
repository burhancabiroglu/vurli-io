package io.vurli.link.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.vurli.ClientHeaderFilter;
import io.vurli.Mailpit;
import io.vurli.TestAccounts;
import io.vurli.TestcontainersConfiguration;
import io.vurli.account.AccountManagement;
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
class LinkApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AccountManagement accounts;

	@Autowired
	private Mailpit mailpit;

	@Test
	void verifiedUserCreatesAChangesAndDeletesALink() throws Exception {
		Cookie session = verifiedSession();

		String body = this.mvc.perform(json(post("/api/v1/links"), session, "{\"targetUrl\": \"https://example.com/menu\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.version").value(0))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(body, "$.id");
		String code = JsonPath.read(body, "$.code");
		this.mvc.perform(get("/" + code))
			.andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/menu"));

		this.mvc.perform(json(put("/api/v1/links/" + id + "/target"), session,
				"{\"version\": 0, \"targetUrl\": \"https://example.com/new\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1));
		this.mvc.perform(json(post("/api/v1/links/" + id + "/disable"), session, "{\"version\": 0}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("version_conflict"));
		this.mvc.perform(delete("/api/v1/links/" + id).queryParam("version", "1")
			.header(ClientHeaderFilter.HEADER, "test")
			.cookie(session)).andExpect(status().isNoContent());
		this.mvc.perform(get("/" + code)).andExpect(status().isGone());
	}

	@Test
	void unverifiedUserCannotCreateLinks() throws Exception {
		String email = TestAccounts.registerUnverified(this.accounts);
		Cookie session = TestAccounts.login(this.mvc, email, TestAccounts.PASSWORD);

		this.mvc.perform(json(post("/api/v1/links"), session, "{\"targetUrl\": \"https://example.com\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("email_not_verified"));
	}

	@Test
	void anotherAccountCannotSeeOrTouchTheLink() throws Exception {
		Cookie owner = verifiedSession();
		Cookie other = verifiedSession();
		String body = this.mvc.perform(json(post("/api/v1/links"), owner, "{\"targetUrl\": \"https://example.com/private\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(body, "$.id");

		this.mvc.perform(get("/api/v1/links/" + id).cookie(other))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("link_not_found"));
		this.mvc.perform(json(put("/api/v1/links/" + id + "/target"), other,
				"{\"version\": 0, \"targetUrl\": \"https://evil.com\"}"))
			.andExpect(status().isNotFound());
		this.mvc.perform(json(post("/api/v1/links/" + id + "/disable"), other, "{\"version\": 0}"))
			.andExpect(status().isNotFound());
		this.mvc.perform(delete("/api/v1/links/" + id).queryParam("version", "0")
			.header(ClientHeaderFilter.HEADER, "test")
			.cookie(other)).andExpect(status().isNotFound());
		this.mvc.perform(get("/api/v1/links").cookie(other)).andExpect(jsonPath("$.length()").value(0));

		this.mvc.perform(get("/api/v1/links/" + id).cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.targetUrl").value("https://example.com/private"));
	}

	@Test
	void linksRequireASession() throws Exception {
		this.mvc.perform(get("/api/v1/links")).andExpect(status().isUnauthorized());
		this.mvc
			.perform(post("/api/v1/links").header(ClientHeaderFilter.HEADER, "test")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"targetUrl\": \"https://example.com\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void invalidTargetIsRejectedWithReason() throws Exception {
		this.mvc.perform(json(post("/api/v1/links"), verifiedSession(), "{\"targetUrl\": \"https://user@example.com\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("invalid_target"))
			.andExpect(jsonPath("$.reason").value("CREDENTIALS"));
	}

	private Cookie verifiedSession() throws Exception {
		String email = TestAccounts.registerVerified(this.accounts, this.mailpit);
		return TestAccounts.login(this.mvc, email, TestAccounts.PASSWORD);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Cookie session,
			String body) {
		return request.header(ClientHeaderFilter.HEADER, "test")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

}
