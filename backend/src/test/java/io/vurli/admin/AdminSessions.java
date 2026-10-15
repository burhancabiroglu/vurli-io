package io.vurli.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.vurli.Mailpit;
import io.vurli.TestAccounts;
import io.vurli.account.AccountManagement;
import io.vurli.account.internal.TestTotp;
import jakarta.servlet.http.Cookie;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** A fully signed-in administrator, created the way a real one would be. */
final class AdminSessions {

	static final String ADMIN = "http://admin.vurli.io";

	static final String COOKIE = "VURLI_ADMIN_SESSION";

	private static final Pattern SECRET = Pattern.compile("class=\"secret\">([A-Z2-7]+)<");

	private AdminSessions() {
	}

	record Admin(String email, Cookie session) {
	}

	static Admin signIn(MockMvc mvc, AccountManagement accounts, Mailpit mailpit, JdbcTemplate jdbc) throws Exception {
		String email = TestAccounts.registerVerified(accounts, mailpit);
		jdbc.update("update users set role = 'ADMIN' where email = ?", email);
		Cookie halfway = mvc
			.perform(post(ADMIN + "/admin/login").param("email", email).param("password", TestAccounts.PASSWORD).with(csrf()))
			.andReturn()
			.getResponse()
			.getCookie(COOKIE);
		String setup = mvc.perform(get(ADMIN + "/admin/login/setup").cookie(halfway))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Matcher secret = SECRET.matcher(setup);
		if (!secret.find()) {
			throw new AssertionError("No TOTP secret on the setup page");
		}
		Cookie session = mvc
			.perform(post(ADMIN + "/admin/login/setup").param("code", TestTotp.codeNow(secret.group(1)))
				.with(csrf())
				.cookie(halfway))
			.andReturn()
			.getResponse()
			.getCookie(COOKIE);
		return new Admin(email, session);
	}

}
