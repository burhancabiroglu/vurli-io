package io.vurli;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

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

	static final String ADMIN_COOKIE_NAME = "VURLI_ADMIN_SESSION";

	/**
	 * Two cookies (K43): the panel's and the admin panel's. They never stand in for each
	 * other, even where both apps share a host, as in local development.
	 * <ul>
	 * <li>Panel: HttpOnly keeps it away from JavaScript, SameSite=Lax off cross-site
	 * POSTs, and no Domain attribute on the host that set it (app.vurli.io), never on
	 * vurli.io where short links live.</li>
	 * <li>Admin: the same, but SameSite=Strict and only sent to {@code /admin}.</li>
	 * </ul>
	 */
	@Bean
	CookieSerializer cookieSerializer(@Value("${vurli.session.secure-cookie:true}") boolean secure) {
		DefaultCookieSerializer panel = serializer(COOKIE_NAME, "/", "Lax", secure);
		DefaultCookieSerializer admin = serializer(ADMIN_COOKIE_NAME, "/admin", "Strict", secure);
		return new CookieSerializer() {

			@Override
			public void writeCookieValue(CookieValue cookieValue) {
				pick(cookieValue.getRequest()).writeCookieValue(cookieValue);
			}

			@Override
			public List<String> readCookieValues(HttpServletRequest request) {
				return pick(request).readCookieValues(request);
			}

			private CookieSerializer pick(HttpServletRequest request) {
				String path = request.getRequestURI();
				return (path.equals("/admin") || path.startsWith("/admin/")) ? admin : panel;
			}

		};
	}

	private static DefaultCookieSerializer serializer(String name, String path, String sameSite, boolean secure) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(name);
		serializer.setCookiePath(path);
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite(sameSite);
		serializer.setUseSecureCookie(secure);
		return serializer;
	}

}
