package io.vurli.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.vurli.Mailpit;
import io.vurli.TestAccounts;
import io.vurli.TestcontainersConfiguration;
import io.vurli.account.AccountManagement;
import io.vurli.account.internal.TestTotp;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The admin sign-in on admin.vurli.io: password, then a TOTP code or a backup code, with a
 * new session id after each step.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminLoginIntegrationTests {

	private static final String ADMIN = "http://admin.vurli.io";

	private static final String COOKIE = "VURLI_ADMIN_SESSION";

	private static final Pattern SECRET = Pattern.compile("class=\"secret\">([A-Z2-7]+)<");

	private static final Pattern BACKUP_CODE = Pattern.compile("<code>([A-Z2-9]{5}-[A-Z2-9]{5})</code>");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AccountManagement accounts;

	@Autowired
	private Mailpit mailpit;

	@Autowired
	private JdbcTemplate jdbc;

	private String email;

	@BeforeEach
	void createAdministrator() {
		this.email = TestAccounts.registerVerified(this.accounts, this.mailpit);
		this.jdbc.update("update users set role = 'ADMIN' where email = ?", this.email);
	}

	@Test
	void firstSignInSetsUpTheSecondFactorAndShowsBackupCodesOnce() throws Exception {
		MockHttpServletResponse password = signInWithPassword(this.email, TestAccounts.PASSWORD);
		assertThat(password.getRedirectedUrl()).isEqualTo("/admin/login/setup");
		assertThat(password.getHeader(HttpHeaders.SET_COOKIE)).contains("Path=/admin")
			.contains("HttpOnly")
			.contains("SameSite=Strict");
		Cookie halfway = password.getCookie(COOKIE);

		String setupPage = page(get(ADMIN + "/admin/login/setup"), halfway);
		String secret = match(SECRET, setupPage);
		// Reloading shows the same secret, so an app that already scanned it keeps working.
		assertThat(match(SECRET, page(get(ADMIN + "/admin/login/setup"), halfway))).isEqualTo(secret);
		assertThat(page(post(ADMIN + "/admin/login/setup").param("code", "000000"), halfway))
			.contains("This code is wrong");

		MockHttpServletResponse confirmed = this.mvc
			.perform(post(ADMIN + "/admin/login/setup").param("code", TestTotp.codeNow(secret))
				.with(csrf())
				.cookie(halfway))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
		List<String> codes = BACKUP_CODE.matcher(confirmed.getContentAsString()).results().map((m) -> m.group(1)).toList();
		assertThat(codes).hasSize(10).doesNotHaveDuplicates();
		assertThat(confirmed.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
		Cookie signedIn = confirmed.getCookie(COOKIE);
		assertThat(signedIn.getValue()).isNotEqualTo(halfway.getValue());

		assertThat(page(get(ADMIN + "/admin"), signedIn)).contains(this.email);
		this.mvc.perform(get(ADMIN + "/admin").cookie(halfway)).andExpect(redirectedUrl("/admin/login"));
		assertThat(auditActions()).contains("ADMIN_TOTP_ENROLLED", "ADMIN_LOGIN");
		assertThat(this.jdbc.queryForObject("""
				select count(*) from admin_backup_codes c join users u on u.id = c.user_id
				where u.email = ? and c.used_at is null""", Integer.class, this.email)).isEqualTo(10);
	}

	@Test
	void codesWorkOnceWhetherFromTheAppOrABackupCode() throws Exception {
		Enrolled enrolled = enroll();

		// The code used during setup cannot be replayed within its 30 seconds.
		Cookie halfway = signInWithPassword(this.email, TestAccounts.PASSWORD).getCookie(COOKIE);
		assertThat(page(post(ADMIN + "/admin/login/code").param("code", TestTotp.codeNow(enrolled.secret())), halfway))
			.contains("This code is wrong");

		MockHttpServletResponse withBackup = this.mvc
			.perform(post(ADMIN + "/admin/login/code").param("code", enrolled.backupCodes().get(0).toLowerCase())
				.with(csrf())
				.cookie(halfway))
			.andExpect(redirectedUrl("/admin"))
			.andReturn()
			.getResponse();
		assertThat(page(get(ADMIN + "/admin"), withBackup.getCookie(COOKIE))).contains(this.email);

		Cookie again = signInWithPassword(this.email, TestAccounts.PASSWORD).getCookie(COOKIE);
		assertThat(page(post(ADMIN + "/admin/login/code").param("code", enrolled.backupCodes().get(0)), again))
			.contains("This code is wrong");
		assertThat(auditActions()).contains("ADMIN_BACKUP_CODE_USED", "ADMIN_CODE_REJECTED");
	}

	@Test
	void aPasswordOnlySessionNeverOpensTheAdminPanel() throws Exception {
		enroll();
		// The panel's API session of the same administrator: ROLE_ADMIN, but no second factor.
		Cookie apiSession = TestAccounts.login(this.mvc, this.email, TestAccounts.PASSWORD);
		Cookie halfway = signInWithPassword(this.email, TestAccounts.PASSWORD).getCookie(COOKIE);

		for (Cookie cookie : new Cookie[] { apiSession, new Cookie(COOKIE, apiSession.getValue()), halfway }) {
			this.mvc.perform(get(ADMIN + "/admin").cookie(cookie))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/admin/login"));
		}
	}

	@Test
	void anAccountThatIsNotAnAdministratorGetsTheWrongPasswordAnswer() throws Exception {
		String user = TestAccounts.registerVerified(this.accounts, this.mailpit);

		String notAdmin = this.mvc.perform(passwordStep(user, TestAccounts.PASSWORD))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String wrongPassword = this.mvc.perform(passwordStep(this.email, "wrong password"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(withoutCsrfToken(notAdmin)).contains("Wrong email or password.")
			.isEqualTo(withoutCsrfToken(wrongPassword));
	}

	@Test
	void tooManyWrongCodesStopEvenTheRightOne() throws Exception {
		Enrolled enrolled = enroll();
		Cookie halfway = signInWithPassword(this.email, TestAccounts.PASSWORD).getCookie(COOKIE);
		for (int i = 0; i < 5; i++) {
			assertThat(page(post(ADMIN + "/admin/login/code").param("code", "000000"), halfway))
				.contains("This code is wrong");
		}

		assertThat(page(post(ADMIN + "/admin/login/code").param("code", enrolled.backupCodes().get(1)), halfway))
			.contains("Too many attempts");
	}

	@Test
	void formsNeedACsrfToken() throws Exception {
		this.mvc.perform(post(ADMIN + "/admin/login").param("email", this.email).param("password", TestAccounts.PASSWORD))
			.andExpect(status().isForbidden());
		assertThat(this.jdbc.queryForObject("""
				select count(*) from audit_log a join users u on u.id = a.actor_id where u.email = ?""", Integer.class,
				this.email)).isZero();
	}

	@Test
	void theAdminPanelAnswersOnItsOwnHostOnly() throws Exception {
		this.mvc.perform(get(ADMIN + "/admin/login"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Security-Policy", startsWith("default-src 'none'")));
		this.mvc.perform(get(ADMIN + "/admin/assets/admin.css")).andExpect(status().isOk());
		for (String host : new String[] { "http://vurli.io", "http://localhost", "http://evil.com" }) {
			this.mvc.perform(get(host + "/admin/login")).andExpect(status().isNotFound());
			this.mvc.perform(get(host + "/admin")).andExpect(status().isNotFound());
		}
		// The rest of the application is not reachable through the admin host.
		this.mvc.perform(get(ADMIN + "/api/v1/auth/me")).andExpect(status().isNotFound());
	}

	@Test
	void signingOutEndsTheSession() throws Exception {
		Cookie signedIn = enroll().session();

		this.mvc.perform(post(ADMIN + "/admin/logout").with(csrf()).cookie(signedIn))
			.andExpect(redirectedUrl("/admin/login?logout"));
		this.mvc.perform(get(ADMIN + "/admin").cookie(signedIn)).andExpect(redirectedUrl("/admin/login"));
	}

	@Test
	void pagesOfTheSecondStepNeedThePasswordFirst() throws Exception {
		this.mvc.perform(get(ADMIN + "/admin/login/code")).andExpect(redirectedUrl("/admin/login"));
		this.mvc.perform(get(ADMIN + "/admin/login/setup")).andExpect(redirectedUrl("/admin/login"));
		this.mvc.perform(post(ADMIN + "/admin/login/code").param("code", "123456").with(csrf()))
			.andExpect(redirectedUrl("/admin/login"));
	}

	private record Enrolled(String secret, List<String> backupCodes, Cookie session) {
	}

	private Enrolled enroll() throws Exception {
		Cookie halfway = signInWithPassword(this.email, TestAccounts.PASSWORD).getCookie(COOKIE);
		String secret = match(SECRET, page(get(ADMIN + "/admin/login/setup"), halfway));
		MockHttpServletResponse confirmed = this.mvc
			.perform(post(ADMIN + "/admin/login/setup").param("code", TestTotp.codeNow(secret))
				.with(csrf())
				.cookie(halfway))
			.andReturn()
			.getResponse();
		List<String> codes = BACKUP_CODE.matcher(confirmed.getContentAsString()).results().map((m) -> m.group(1)).toList();
		return new Enrolled(secret, codes, confirmed.getCookie(COOKIE));
	}

	private MockHttpServletResponse signInWithPassword(String email, String password) throws Exception {
		return this.mvc.perform(passwordStep(email, password)).andExpect(status().isFound()).andReturn().getResponse();
	}

	private static MockHttpServletRequestBuilder passwordStep(String email, String password) {
		return post(ADMIN + "/admin/login").param("email", email).param("password", password).with(csrf());
	}

	/** A page in English; the CSRF token is harmless on GET and needed on POST. */
	private String page(MockHttpServletRequestBuilder request, Cookie session) throws Exception {
		return this.mvc.perform(request.with(csrf()).cookie(session).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	/** The token differs on every page and says nothing about the account. */
	private static String withoutCsrfToken(String page) {
		return page.replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "name=\"_csrf\"");
	}

	private List<String> auditActions() {
		return this.jdbc.queryForList("""
				select a.action from audit_log a join users u on u.id = a.actor_id where u.email = ?""", String.class,
				this.email);
	}

	private static String match(Pattern pattern, String text) {
		Matcher matcher = pattern.matcher(text);
		assertThat(matcher.find()).as("pattern %s in page", pattern).isTrue();
		return matcher.group(1);
	}

}
