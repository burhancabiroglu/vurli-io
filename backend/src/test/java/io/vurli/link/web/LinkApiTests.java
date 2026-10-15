package io.vurli.link.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
		assertThat((String) JsonPath.read(body, "$.shortUrl")).isEqualTo("https://vurli.io/" + code);
		this.mvc.perform(get("http://vurli.io/" + code))
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
		this.mvc.perform(get("http://vurli.io/" + code)).andExpect(status().isGone());
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
		this.mvc.perform(get("/api/v1/links/" + id + "/qr").cookie(other))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("link_not_found"));
		this.mvc.perform(get("/api/v1/links").cookie(other)).andExpect(jsonPath("$.items.length()").value(0));

		this.mvc.perform(get("/api/v1/links/" + id).cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.targetUrl").value("https://example.com/private"));
	}

	@Test
	void qrCodeDownloadsAsPngOrSvg() throws Exception {
		Cookie session = verifiedSession();
		String body = this.mvc.perform(json(post("/api/v1/links"), session,
				"{\"targetUrl\": \"https://example.com/menu\", \"alias\": \"qr-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(body, "$.id");
		String code = JsonPath.read(body, "$.code");

		byte[] png = this.mvc.perform(get("/api/v1/links/" + id + "/qr").cookie(session))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vurli-" + code + ".png\""))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		assertThat(png).startsWith(0x89, 'P', 'N', 'G');
		this.mvc.perform(get("/api/v1/links/" + id + "/qr").queryParam("format", "svg").cookie(session))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/svg+xml"))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vurli-" + code + ".svg\""));
		this.mvc.perform(get("/api/v1/links/" + id + "/qr").queryParam("format", "gif").cookie(session))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_format"));
	}

	@Test
	void listTellsWhetherMoreLinksFollow() throws Exception {
		Cookie session = verifiedSession();
		for (int i = 0; i < 3; i++) {
			this.mvc.perform(json(post("/api/v1/links"), session, "{\"targetUrl\": \"https://example.com/" + i + "\"}"))
				.andExpect(status().isCreated());
		}

		this.mvc.perform(get("/api/v1/links").queryParam("size", "2").cookie(session))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.hasNext").value(true));
		this.mvc.perform(get("/api/v1/links").queryParam("page", "1").queryParam("size", "2").cookie(session))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.hasNext").value(false));
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
