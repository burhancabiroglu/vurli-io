package io.vurli;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * The session cookie, defined in code rather than through {@code server.servlet.session.*}
 * properties: Spring Boot only applies those when it runs an embedded server, so they
 * silently vanished in MockMvc tests. A security-relevant cookie must not depend on how
 * the application is started.
 */
@Configuration(proxyBeanMethods = false)
class SessionCookieConfiguration {

	static final String COOKIE_NAME = "VURLI_SESSION";

	/**
	 * HttpOnly keeps the cookie away from JavaScript, SameSite=Lax keeps it off
	 * cross-site POSTs, and no Domain attribute keeps it on the host that set it
	 * (app.vurli.io), never on vurli.io where short links live.
	 */
	@Bean
	CookieSerializer cookieSerializer(@Value("${vurli.session.secure-cookie:true}") boolean secure) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(COOKIE_NAME);
		serializer.setCookiePath("/");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		serializer.setUseSecureCookie(secure);
		return serializer;
	}

}
