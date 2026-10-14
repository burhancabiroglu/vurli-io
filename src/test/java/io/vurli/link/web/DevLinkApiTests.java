package io.vurli.link.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import io.vurli.TestcontainersConfiguration;
import io.vurli.link.CreateLinkRequest;
import io.vurli.link.LinkManagement;
import io.vurli.link.LinkView;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevLinkApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private LinkManagement linkManagement;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void createdLinkRedirectsAndCanBeChanged() throws Exception {
		String body = this.mvc
			.perform(post("/dev/api/links").contentType(MediaType.APPLICATION_JSON)
				.content("{\"targetUrl\": \"https://example.com/menu\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").isString())
			.andExpect(jsonPath("$.version").value(0))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(body, "$.id");
		String code = JsonPath.read(body, "$.code");

		this.mvc.perform(get("/" + code))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/menu"));

		this.mvc
			.perform(put("/dev/api/links/" + id + "/target").contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\": 0, \"targetUrl\": \"https://example.com/new\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1));
		this.mvc.perform(get("/" + code)).andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/new"));

		this.mvc.perform(delete("/dev/api/links/" + id).queryParam("version", "1")).andExpect(status().isNoContent());
		this.mvc.perform(get("/" + code)).andExpect(status().isGone());
	}

	@Test
	void staleVersionIsAConflict() throws Exception {
		LinkView link = createDirectly();
		this.linkManagement.disable(devWorkspace(), link.id(), 0);

		this.mvc
			.perform(post("/dev/api/links/" + link.id() + "/enable").contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\": 0}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("version_conflict"));
	}

	@Test
	void invalidTargetIsRejectedWithReason() throws Exception {
		this.mvc
			.perform(post("/dev/api/links").contentType(MediaType.APPLICATION_JSON)
				.content("{\"targetUrl\": \"https://user@example.com\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("invalid_target"))
			.andExpect(jsonPath("$.reason").value("CREDENTIALS"));
	}

	@Test
	void takenAliasIsAConflict() throws Exception {
		String alias = "dev-" + UUID.randomUUID().toString().substring(0, 8);
		String request = "{\"targetUrl\": \"https://example.com\", \"alias\": \"" + alias + "\"}";
		this.mvc.perform(post("/dev/api/links").contentType(MediaType.APPLICATION_JSON).content(request))
			.andExpect(status().isCreated());

		this.mvc.perform(post("/dev/api/links").contentType(MediaType.APPLICATION_JSON).content(request))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("alias_taken"));
	}

	@Test
	void linksOfOtherWorkspacesAreNotFound() throws Exception {
		UUID otherWorkspace = UUID.randomUUID();
		this.jdbc.update("insert into workspaces (id, name, created_at) values (?, 'other', now())", otherWorkspace);
		LinkView theirs = this.linkManagement.create(otherWorkspace, CreateLinkRequest.of("https://example.com"));

		this.mvc.perform(get("/dev/api/links/" + theirs.id()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("link_not_found"));
	}

	private LinkView createDirectly() {
		return this.linkManagement.create(devWorkspace(), CreateLinkRequest.of("https://example.com/menu"));
	}

	private static UUID devWorkspace() {
		return UUID.fromString("00000000-0000-0000-0000-000000000001");
	}

}
