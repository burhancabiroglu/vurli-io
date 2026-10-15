package io.vurli.abuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.vurli.TestAccounts;
import io.vurli.TestcontainersConfiguration;
import io.vurli.link.CreateLinkRequest;
import io.vurli.link.LinkManagement;
import io.vurli.link.LinkView;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The public report form on vurli.io/abuse, with a real CSRF token from the page. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AbuseReportIntegrationTests {

	private static final String FORM = "http://vurli.io/abuse";

	private static final Pattern TOKEN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private LinkManagement links;

	@Autowired
	private JdbcTemplate jdbc;

	private LinkView link;

	@BeforeEach
	void createLink() {
		UUID workspaceId = UUID.randomUUID();
		this.jdbc.update("insert into workspaces (id, name, created_at) values (?, 'test', now())", workspaceId);
		this.link = this.links.create(workspaceId,
				new CreateLinkRequest("https://example.com/kampanya", "bildir-" + UUID.randomUUID().toString().substring(0, 8), null));
	}

	@Test
	void theFormNeedsNoSessionAndItsTokenStaysOnTheFormPath() throws Exception {
		MockHttpServletResponse page = this.mvc.perform(get(FORM)).andExpect(status().isOk()).andReturn().getResponse();

		// The token cookie only: no session on the short link domain.
		assertThat(page.getCookies()).extracting(Cookie::getName).containsExactly("VURLI_REPORT_CSRF");
		Cookie token = page.getCookie("VURLI_REPORT_CSRF");
		assertThat(token.getPath()).isEqualTo("/abuse");
		assertThat(token.isHttpOnly()).isTrue();
		assertThat(token.getAttribute("SameSite")).isEqualTo("Strict");
		assertThat(page.getHeader("Content-Security-Policy")).startsWith("default-src 'none'");
		assertThat(page.getContentAsString()).contains("name=\"_csrf\"");
	}

	@Test
	void aReportIsStoredWithTheLinkItNames() throws Exception {
		String thanks = submit(Map.of("link", "https://vurli.io/" + this.link.code(), "reason", "PHISHING",
				"details", "şifre istiyor", "email", "Bildiren@Example.com")).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(thanks).contains("Thank you");
		Map<String, Object> row = this.jdbc.queryForMap(
				"select link_id, reason, details, reporter_email, status from abuse_reports where code = ?",
				this.link.code());
		assertThat(row).containsEntry("link_id", this.link.id())
			.containsEntry("reason", "PHISHING")
			.containsEntry("details", "şifre istiyor")
			.containsEntry("reporter_email", "bildiren@example.com")
			.containsEntry("status", "OPEN");
	}

	@Test
	void theAnswerIsTheSameWhetherTheLinkExistsOrNot() throws Exception {
		String existing = withoutToken(submit(Map.of("link", this.link.code(), "reason", "SPAM")).andReturn()
			.getResponse()
			.getContentAsString());
		String missing = withoutToken(submit(Map.of("link", "yok-" + UUID.randomUUID().toString().substring(0, 8),
				"reason", "SPAM")).andReturn().getResponse().getContentAsString());

		assertThat(missing).isEqualTo(existing);
	}

	@Test
	void aBadLinkKeepsWhatWasTyped() throws Exception {
		String page = submit(Map.of("link", "not a link", "reason", "MALWARE", "details", "ayrıntı burada"))
			.andExpect(status().isUnprocessableContent())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(page).contains("Enter a Vurli short link").contains("ayrıntı burada").contains("value=\"not a link\"");
		assertThat(this.jdbc.queryForObject("select count(*) from abuse_reports where details = 'ayrıntı burada'",
				Integer.class)).isZero();
	}

	@Test
	void aPostWithoutTheFormsTokenIsRefused() throws Exception {
		this.mvc.perform(post(FORM).param("link", this.link.code()).param("reason", "SPAM"))
			.andExpect(status().isForbidden());
		assertThat(this.jdbc.queryForObject("select count(*) from abuse_reports where code = ?", Integer.class,
				this.link.code())).isZero();
	}

	@Test
	void reportsAreLimitedPerAddress() throws Exception {
		String address = TestAccounts.randomAddress();
		for (int i = 0; i < 5; i++) {
			submitFrom(address, Map.of("link", this.link.code(), "reason", "SPAM")).andExpect(status().isOk());
		}

		submitFrom(address, Map.of("link", this.link.code(), "reason", "SPAM")).andExpect(status().isTooManyRequests());
		assertThat(this.jdbc.queryForObject("select count(*) from abuse_reports where code = ?", Integer.class,
				this.link.code())).isEqualTo(5);
	}

	private ResultActions submit(Map<String, String> fields) throws Exception {
		return submitFrom(TestAccounts.randomAddress(), fields);
	}

	/** Like a browser: load the form, then post it with the token and the cookie it set. */
	private ResultActions submitFrom(String address, Map<String, String> fields)
			throws Exception {
		MockHttpServletResponse page = this.mvc
			.perform(get(FORM).header(HttpHeaders.ACCEPT_LANGUAGE, "en").with((request) -> {
				request.setRemoteAddr(address);
				return request;
			}))
			.andReturn()
			.getResponse();
		Matcher token = TOKEN.matcher(page.getContentAsString());
		assertThat(token.find()).isTrue();
		Cookie cookie = page.getCookie("VURLI_REPORT_CSRF");
		MockHttpServletRequestBuilder form = post(FORM).header(HttpHeaders.ACCEPT_LANGUAGE, "en")
			.param("_csrf", token.group(1))
			.cookie(cookie)
			.with((request) -> {
				request.setRemoteAddr(address);
				return request;
			});
		fields.forEach((name, value) -> form.param(name, value));
		return this.mvc.perform(form);
	}

	private static String withoutToken(String page) {
		return page.replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "name=\"_csrf\"");
	}

}
