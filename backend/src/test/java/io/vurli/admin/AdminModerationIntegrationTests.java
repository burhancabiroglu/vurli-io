package io.vurli.admin;

import static io.vurli.admin.AdminSessions.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import io.vurli.ClientHeaderFilter;
import io.vurli.Mailpit;
import io.vurli.TestAccounts;
import io.vurli.TestcontainersConfiguration;
import io.vurli.account.AccountManagement;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * The admin panel's actions from the outside: through the panel's forms, checked where it
 * matters, at the short link and the owner's API.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminModerationIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AccountManagement accounts;

	@Autowired
	private Mailpit mailpit;

	@Autowired
	private JdbcTemplate jdbc;

	private AdminSessions.Admin admin;

	private String ownerEmail;

	private Cookie owner;

	@BeforeEach
	void setUp() throws Exception {
		this.admin = AdminSessions.signIn(this.mvc, this.accounts, this.mailpit, this.jdbc);
		this.ownerEmail = TestAccounts.registerVerified(this.accounts, this.mailpit);
		this.owner = TestAccounts.login(this.mvc, this.ownerEmail, TestAccounts.PASSWORD);
	}

	@Test
	void aBlockTakesEffectOnTheNextVisitAndTheOwnerCannotUndoIt() throws Exception {
		Map<String, String> link = createLink("https://example.com/kampanya");
		visit(link).andExpect(status().isFound());

		String page = adminPage("/admin/links?q=" + link.get("code"));
		assertThat(page).contains(link.get("code")).contains(this.ownerEmail);
		adminPost("/admin/links/" + link.get("id") + "/block", "phishing raporu #12")
			.andExpect(redirectedUrl("/admin/links?q="))
			.andExpect(flash().attribute("message", "admin.links.blocked"));

		visit(link).andExpect(status().isGone()).andExpect(header().doesNotExist(HttpHeaders.LOCATION));
		this.mvc.perform(ownerJson(post("/api/v1/links/" + link.get("id") + "/enable"), "{\"version\": 1}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("link_blocked"));
		assertThat(audit("LINK_BLOCKED", link.get("id"))).containsEntry("actor", this.admin.email())
			.containsEntry("reason", "phishing raporu #12");

		adminPost("/admin/links/" + link.get("id") + "/unblock", "yanlış alarm").andExpect(status().isFound());
		visit(link).andExpect(status().isFound());
		assertThat(audit("LINK_UNBLOCKED", link.get("id"))).containsEntry("reason", "yanlış alarm");
	}

	@Test
	void everyChangeNeedsAReason() throws Exception {
		Map<String, String> link = createLink("https://example.com/a");

		adminPost("/admin/links/" + link.get("id") + "/block", "  ")
			.andExpect(flash().attribute("error", "admin.error.reason"));
		adminPost("/admin/links/" + link.get("id") + "/block", "x".repeat(501))
			.andExpect(flash().attribute("error", "admin.error.reason"));

		visit(link).andExpect(status().isFound());
		assertThat(this.jdbc.queryForObject("select count(*) from audit_log where target_id = ?", Integer.class,
				link.get("id"))).isZero();
	}

	@Test
	void suspendingAnAccountEndsItsSessionsBlocksItsLinksAndStopsSignIn() throws Exception {
		Map<String, String> first = createLink("https://example.com/1");
		Map<String, String> second = createLink("https://example.com/2");
		UUID ownerId = userId(this.ownerEmail);
		// Cached as redirects, so the block must also clear the cache.
		visit(first).andExpect(status().isFound());
		visit(second).andExpect(status().isFound());

		adminPost("/admin/accounts/" + ownerId + "/suspend", "spam kampanyası")
			.andExpect(flash().attribute("message", "admin.accounts.suspended"));

		this.mvc.perform(get("/api/v1/auth/me").cookie(this.owner)).andExpect(status().isUnauthorized());
		visit(first).andExpect(status().isGone());
		visit(second).andExpect(status().isGone());
		String suspended = login(this.ownerEmail, TestAccounts.PASSWORD).andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String wrongPassword = login(this.ownerEmail, "wrong password").andReturn().getResponse().getContentAsString();
		// A suspended account looks exactly like a wrong password.
		assertThat(suspended).isEqualTo(wrongPassword).contains("invalid_credentials");
		assertThat(audit("ACCOUNT_SUSPENDED", ownerId.toString())).containsEntry("linksBlocked", "2")
			.containsEntry("reason", "spam kampanyası");

		adminPost("/admin/accounts/" + ownerId + "/unsuspend", "itiraz kabul edildi").andExpect(status().isFound());
		login(this.ownerEmail, TestAccounts.PASSWORD).andExpect(status().isOk());
		// Links stay blocked until someone looks at them one by one.
		visit(first).andExpect(status().isGone());
	}

	@Test
	void blockingAllLinksKeepsTheAccountUsable() throws Exception {
		Map<String, String> link = createLink("https://example.com/b");
		UUID ownerId = userId(this.ownerEmail);
		visit(link).andExpect(status().isFound());

		adminPost("/admin/accounts/" + ownerId + "/block-links", "toplu kötüye kullanım").andExpect(status().isFound());

		visit(link).andExpect(status().isGone());
		this.mvc.perform(get("/api/v1/auth/me").cookie(this.owner)).andExpect(status().isOk());
		assertThat(audit("ACCOUNT_LINKS_BLOCKED", ownerId.toString())).containsEntry("linksBlocked", "1");
	}

	@Test
	void administratorsCannotBeSuspended() throws Exception {
		UUID adminId = userId(this.admin.email());

		adminPost("/admin/accounts/" + adminId + "/suspend", "deneme")
			.andExpect(flash().attribute("error", "admin.error.administrator"));

		this.mvc.perform(get(ADMIN + "/admin").cookie(this.admin.session())).andExpect(status().isOk());
		assertThat(this.jdbc.queryForObject("select suspended_at is null from users where id = ?", Boolean.class,
				adminId)).isTrue();
	}

	@Test
	void searchTreatsWildcardsAsText() throws Exception {
		Map<String, String> percent = createLink("https://example.com/%25-indirim");
		Map<String, String> plain = createLink("https://example.com/kampanya-x");

		// "%" and "_" are LIKE wildcards; searching for them must find only targets that contain them.
		String page = this.mvc.perform(get(ADMIN + "/admin/links").param("q", "%").cookie(this.admin.session()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(page).contains(percent.get("code")).doesNotContain(plain.get("code"));
		assertThat(adminPage("/admin/links?q=" + plain.get("code"))).contains(plain.get("code"))
			.doesNotContain(percent.get("code"));
		assertThat(adminPage("/admin/accounts?q=_")).doesNotContain(this.ownerEmail);
		assertThat(adminPage("/admin/accounts?q=" + this.ownerEmail.substring(0, 20))).contains(this.ownerEmail);
	}

	@Test
	void pagesEscapeWhatTheyShow() throws Exception {
		String page = this.mvc
			.perform(get(ADMIN + "/admin/links").param("q", "<script>alert(1)</script>").cookie(this.admin.session()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(page).doesNotContain("<script>alert(1)</script>").contains("&lt;script&gt;alert(1)&lt;/script&gt;");
	}

	@Test
	void theAuditLogShowsWhoDidWhat() throws Exception {
		Map<String, String> link = createLink("https://example.com/c");
		adminPost("/admin/links/" + link.get("id") + "/block", "kontrol").andExpect(status().isFound());

		assertThat(adminPage("/admin/audit")).contains("LINK_BLOCKED")
			.contains(this.admin.email())
			.contains("ADMIN_LOGIN")
			.contains("kontrol");
	}

	@Test
	void actionsNeedTheSecondFactor() throws Exception {
		Map<String, String> link = createLink("https://example.com/d");
		// The administrator's password-only panel session, presented as an admin session.
		Cookie apiSession = TestAccounts.login(this.mvc, this.admin.email(), TestAccounts.PASSWORD);

		this.mvc.perform(post(ADMIN + "/admin/links/" + link.get("id") + "/block").param("reason", "atlatma denemesi")
			.with(csrf())
			.cookie(new Cookie(AdminSessions.COOKIE, apiSession.getValue())))
			.andExpect(redirectedUrl("/admin/login"));
		this.mvc.perform(post(ADMIN + "/admin/links/" + link.get("id") + "/block").param("reason", "csrf olmadan")
			.cookie(this.admin.session())).andExpect(status().isForbidden());

		visit(link).andExpect(status().isFound());
	}

	private Map<String, String> createLink(String target) throws Exception {
		String body = this.mvc.perform(ownerJson(post("/api/v1/links"), "{\"targetUrl\": \"" + target + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return Map.of("id", JsonPath.read(body, "$.id"), "code", JsonPath.read(body, "$.code"));
	}

	private ResultActions visit(Map<String, String> link) throws Exception {
		return this.mvc.perform(get("http://vurli.io/" + link.get("code")));
	}

	private ResultActions adminPost(String path, String reason) throws Exception {
		return this.mvc.perform(post(ADMIN + path).param("reason", reason).with(csrf()).cookie(this.admin.session()));
	}

	private String adminPage(String path) throws Exception {
		return this.mvc.perform(get(ADMIN + path).cookie(this.admin.session()).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private ResultActions login(String email, String password) throws Exception {
		return this.mvc.perform(post("/api/v1/auth/login").header(ClientHeaderFilter.HEADER, "test")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
	}

	private MockHttpServletRequestBuilder ownerJson(MockHttpServletRequestBuilder request, String body) {
		return request.header(ClientHeaderFilter.HEADER, "test")
			.cookie(this.owner)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	private UUID userId(String email) {
		return this.jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
	}

	/** The entry's details, its actor's email and nothing else, as strings. */
	private Map<String, String> audit(String action, String targetId) {
		List<Map<String, Object>> rows = this.jdbc.queryForList("""
				select u.email as actor, d.key, d.value from audit_log a
				left join users u on u.id = a.actor_id, jsonb_each_text(a.details) d
				where a.action = ? and a.target_id = ?""", action, targetId);
		assertThat(rows).as("audit entry %s for %s", action, targetId).isNotEmpty();
		Map<String, String> entry = new HashMap<>();
		entry.put("actor", (String) rows.get(0).get("actor"));
		rows.forEach((row) -> entry.put((String) row.get("key"), (String) row.get("value")));
		return entry;
	}

}
