package io.vurli.redirect;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import io.vurli.TestcontainersConfiguration;
import io.vurli.link.CreateLinkRequest;
import io.vurli.link.LinkManagement;
import io.vurli.link.LinkModeration;
import io.vurli.link.LinkView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RedirectIntegrationTests {

	/** Short links answer on the public host only (vurli.hosts.public). */
	private static final String SHORT = "http://vurli.io";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private LinkManagement linkManagement;

	@Autowired
	private LinkModeration linkModeration;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID workspaceId;

	@BeforeEach
	void setUp() {
		this.workspaceId = UUID.randomUUID();
		this.jdbc.update("insert into workspaces (id, name, created_at) values (?, 'test', now())", this.workspaceId);
	}

	@Test
	void activeLinkRedirectsWithoutCachingOrSession() throws Exception {
		LinkView link = create("https://example.com/menu?table=4");

		this.mvc.perform(get(SHORT + "/" + link.code()))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/menu?table=4"))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void headGetsTheSameRedirectWithoutBody() throws Exception {
		LinkView link = create("https://example.com/menu");

		this.mvc.perform(head(SHORT + "/" + link.code()))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/menu"))
			.andExpect(content().string(""));
	}

	@Test
	void visitorQueryStringIsNotForwarded() throws Exception {
		LinkView link = create("https://example.com/menu");

		this.mvc.perform(get(SHORT + "/" + link.code()).queryParam("target", "https://evil.com"))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/menu"));
	}

	@Test
	void unknownCodeIsNotFound() throws Exception {
		this.mvc.perform(get(SHORT + "/yok-boyle-bir-link"))
			.andExpect(status().isNotFound())
			.andExpect(header().doesNotExist(HttpHeaders.LOCATION))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
	}

	@Test
	void inactiveDeletedAndExpiredLinksAreGone() throws Exception {
		LinkView disabled = create("https://example.com/a");
		LinkView blocked = create("https://example.com/b");
		LinkView deleted = create("https://example.com/c");
		LinkView expired = create("https://example.com/d");
		this.jdbc.update("update links set status = 'DISABLED' where id = ?", disabled.id());
		this.jdbc.update("update links set status = 'BLOCKED' where id = ?", blocked.id());
		this.jdbc.update("update links set deleted_at = now() where id = ?", deleted.id());
		this.jdbc.update("update links set expires_at = now() - interval '1 second' where id = ?", expired.id());

		for (LinkView link : new LinkView[] { disabled, blocked, deleted, expired }) {
			this.mvc.perform(get(SHORT + "/" + link.code()))
				.andExpect(status().isGone())
				.andExpect(header().doesNotExist(HttpHeaders.LOCATION));
		}
	}

	@Test
	void creatingALinkClearsACachedMiss() throws Exception {
		String alias = "kampanya-" + UUID.randomUUID().toString().substring(0, 8);
		this.mvc.perform(get(SHORT + "/" + alias)).andExpect(status().isNotFound());

		this.linkManagement.create(this.workspaceId, new CreateLinkRequest("https://example.com/yeni", alias, null));

		this.mvc.perform(get(SHORT + "/" + alias))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/yeni"));
	}

	@Test
	void ownerChangesTakeEffectOnTheNextVisit() throws Exception {
		LinkView link = create("https://example.com/old");
		this.mvc.perform(get(SHORT + "/" + link.code())).andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/old"));

		LinkView changed = this.linkManagement.changeTarget(this.workspaceId, link.id(), 0, "https://example.com/new");
		this.mvc.perform(get(SHORT + "/" + link.code())).andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/new"));

		LinkView disabled = this.linkManagement.disable(this.workspaceId, link.id(), changed.version());
		this.mvc.perform(get(SHORT + "/" + link.code())).andExpect(status().isGone());

		this.linkManagement.enable(this.workspaceId, link.id(), disabled.version());
		this.mvc.perform(get(SHORT + "/" + link.code())).andExpect(status().isFound());
	}

	@Test
	void blockTakesEffectOnTheNextVisit() throws Exception {
		LinkView link = create("https://example.com/phishing");
		this.mvc.perform(get(SHORT + "/" + link.code())).andExpect(status().isFound());

		this.linkModeration.block(link.id());

		this.mvc.perform(get(SHORT + "/" + link.code()))
			.andExpect(status().isGone())
			.andExpect(header().doesNotExist(HttpHeaders.LOCATION));
	}

	@Test
	void deletedLinkIsGoneOnTheNextVisit() throws Exception {
		LinkView link = create("https://example.com/menu");
		this.mvc.perform(get(SHORT + "/" + link.code())).andExpect(status().isFound());

		this.linkManagement.delete(this.workspaceId, link.id(), 0);

		this.mvc.perform(get(SHORT + "/" + link.code())).andExpect(status().isGone());
	}

	@Test
	void healthAnswersOnTheInternalHostOnly() throws Exception {
		this.mvc.perform(get("http://localhost/actuator/health")).andExpect(status().isOk());
		this.mvc.perform(get(SHORT + "/actuator/health")).andExpect(status().isNotFound());
	}

	@Test
	void unknownPathsAreDenied() throws Exception {
		this.mvc.perform(get("/some/other/path")).andExpect(status().isForbidden());
		this.mvc.perform(get("/dev/api/links")).andExpect(status().isForbidden());
	}

	@Test
	void shortLinksAnswerOnThePublicHostOnly() throws Exception {
		LinkView link = create("https://example.com");

		this.mvc.perform(get(SHORT + "/" + link.code())).andExpect(status().isFound());
		this.mvc.perform(get("http://VURLI.IO:443/" + link.code())).andExpect(status().isFound());
		for (String host : new String[] { "http://evil.com", "http://localhost", "http://vurli.io.evil.com" }) {
			this.mvc.perform(get(host + "/" + link.code()))
				.andExpect(status().isNotFound())
				.andExpect(header().doesNotExist(HttpHeaders.LOCATION))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
		}
	}

	@Test
	void apiIsUnreachableThroughThePublicHost() throws Exception {
		for (String path : new String[] { "/api/v1/links", "//api/v1/links", "/api/v1/auth/me", "/actuator/health" }) {
			this.mvc.perform(get(SHORT + path))
				.andExpect(status().isNotFound())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		}
	}

	@Test
	void apiRequiresASessionAndNeverRedirects() throws Exception {
		this.mvc.perform(get("/api/v1/links"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist(HttpHeaders.LOCATION));
	}

	private LinkView create(String targetUrl) {
		return this.linkManagement.create(this.workspaceId, CreateLinkRequest.of(targetUrl));
	}

}
