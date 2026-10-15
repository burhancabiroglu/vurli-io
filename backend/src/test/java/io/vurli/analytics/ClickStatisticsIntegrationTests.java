package io.vurli.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import io.vurli.ClientHeaderFilter;
import io.vurli.Mailpit;
import io.vurli.TestAccounts;
import io.vurli.TestcontainersConfiguration;
import io.vurli.account.AccountManagement;
import io.vurli.analytics.internal.ClickWriter;
import io.vurli.analytics.internal.DailySummaries;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ClickStatisticsIntegrationTests {

	private static final String BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";

	private static final String PHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 "
			+ "(KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AccountManagement accounts;

	@Autowired
	private Mailpit mailpit;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ClickWriter writer;

	@Autowired
	private DailySummaries summaries;

	private Cookie session;

	private UUID linkId;

	private String code;

	private LocalDate today;

	@BeforeEach
	void createLink() throws Exception {
		this.session = TestAccounts.login(this.mvc, TestAccounts.registerVerified(this.accounts, this.mailpit),
				TestAccounts.PASSWORD);
		String body = this.mvc
			.perform(post("/api/v1/links").header(ClientHeaderFilter.HEADER, "test")
				.cookie(this.session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"targetUrl\": \"https://example.com/menu\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		this.linkId = UUID.fromString(JsonPath.read(body, "$.id"));
		this.code = JsonPath.read(body, "$.code");
		this.today = LocalDate.now(ZoneOffset.UTC);
	}

	@Test
	void countsBrowserVisitsAndStoresNoAddress() throws Exception {
		visit(get(shortUrl()).header(HttpHeaders.USER_AGENT, BROWSER)
			.header(HttpHeaders.REFERER, "https://www.instagram.com/p/abc?token=secret"));
		visit(get(shortUrl()).header(HttpHeaders.USER_AGENT, "facebookexternalhit/1.1"));
		visit(get(shortUrl()));
		visit(get(shortUrl()).header(HttpHeaders.USER_AGENT, BROWSER).header("Sec-Purpose", "prefetch;prerender"));
		visit(head(shortUrl()).header(HttpHeaders.USER_AGENT, BROWSER));

		this.writer.flush();

		List<Map<String, Object>> rows = this.jdbc.queryForList(
				"select referrer_host, country, device_type from click_events where link_id = ?", this.linkId);
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0)).containsEntry("referrer_host", "instagram.com")
			.containsEntry("device_type", "DESKTOP")
			.containsEntry("country", null);
		assertThat(this.jdbc.queryForList(
				"select column_name from information_schema.columns where table_name = 'click_events'", String.class))
			.containsExactlyInAnyOrder("id", "link_id", "occurred_at", "referrer_host", "country", "device_type");
	}

	@Test
	void statisticsCombineSummariesWithClicksNotSummarizedYet() throws Exception {
		Instant startOfToday = this.today.atStartOfDay(ZoneOffset.UTC).toInstant();
		click(startOfToday.minus(Duration.ofDays(40)), "old.example", "DE", "DESKTOP");
		click(startOfToday.minus(Duration.ofDays(3)).plusSeconds(60), "instagram.com", "TR", "MOBILE");
		click(startOfToday.minus(Duration.ofDays(3)).plusSeconds(120), "instagram.com", null, "MOBILE");
		click(startOfToday.minusMillis(1), null, "TR", "DESKTOP");
		click(startOfToday, "t.co", "TR", "MOBILE");
		click(startOfToday.plusSeconds(1), "instagram.com", "TR", "TABLET");

		this.summaries.run(this.today);
		// Today's clicks are still raw; running again changes nothing.
		this.summaries.run(this.today);
		assertThat(this.jdbc.queryForObject("select count(*) from link_daily_stats where link_id = ?", Integer.class,
				this.linkId)).isEqualTo(3);

		this.mvc.perform(get("/api/v1/links/" + this.linkId + "/stats").cookie(this.session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalClicks").value(6))
			.andExpect(jsonPath("$.windowClicks").value(5))
			.andExpect(jsonPath("$.from").value(this.today.minusDays(29).toString()))
			.andExpect(jsonPath("$.to").value(this.today.toString()))
			.andExpect(jsonPath("$.days.length()").value(30))
			.andExpect(jsonPath("$.days[26].day").value(this.today.minusDays(3).toString()))
			.andExpect(jsonPath("$.days[26].clicks").value(2))
			.andExpect(jsonPath("$.days[27].clicks").value(0))
			.andExpect(jsonPath("$.days[28].clicks").value(1))
			.andExpect(jsonPath("$.days[29].clicks").value(2))
			.andExpect(jsonPath("$.referrers[0].key").value("instagram.com"))
			.andExpect(jsonPath("$.referrers[0].clicks").value(3))
			.andExpect(jsonPath("$.referrers[?(@.key == null)].clicks").value(1))
			.andExpect(jsonPath("$.countries[0].key").value("TR"))
			.andExpect(jsonPath("$.countries[0].clicks").value(4))
			.andExpect(jsonPath("$.countries[1].key").doesNotExist())
			.andExpect(jsonPath("$.devices[0].key").value("MOBILE"))
			.andExpect(jsonPath("$.devices[0].clicks").value(3));
	}

	@Test
	void rawClicksPastRetentionAreSummarizedBeforeTheyAreDeleted() throws Exception {
		Instant old = this.today.atStartOfDay(ZoneOffset.UTC).toInstant().minus(Duration.ofDays(100));
		click(old, "old.example", "DE", "DESKTOP");

		this.summaries.run(this.today);

		assertThat(this.jdbc.queryForObject("select count(*) from click_events where link_id = ?", Integer.class,
				this.linkId)).isZero();
		this.mvc.perform(get("/api/v1/links/" + this.linkId + "/stats").cookie(this.session))
			.andExpect(jsonPath("$.totalClicks").value(1))
			.andExpect(jsonPath("$.windowClicks").value(0));
	}

	@Test
	void anotherAccountCannotReadTheStatistics() throws Exception {
		Cookie other = TestAccounts.login(this.mvc, TestAccounts.registerVerified(this.accounts, this.mailpit),
				TestAccounts.PASSWORD);

		this.mvc.perform(get("/api/v1/links/" + this.linkId + "/stats").cookie(other))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("link_not_found"));
		this.mvc.perform(get("/api/v1/links/" + this.linkId + "/stats")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/v1/links/" + UUID.randomUUID() + "/stats").cookie(this.session))
			.andExpect(status().isNotFound());
	}

	@Test
	void deviceAndBrowserFromARealVisit() throws Exception {
		visit(get(shortUrl()).header(HttpHeaders.USER_AGENT, PHONE).header(HttpHeaders.REFERER, "android-app://com.whatsapp/"));
		this.writer.flush();

		this.mvc.perform(get("/api/v1/links/" + this.linkId + "/stats").cookie(this.session))
			.andExpect(jsonPath("$.totalClicks").value(1))
			.andExpect(jsonPath("$.days[29].clicks").value(1))
			.andExpect(jsonPath("$.devices[0].key").value("MOBILE"))
			.andExpect(jsonPath("$.referrers[0].key").doesNotExist());
	}

	private String shortUrl() {
		return "http://vurli.io/" + this.code;
	}

	private void visit(MockHttpServletRequestBuilder request) throws Exception {
		this.mvc.perform(request).andExpect(status().isFound());
	}

	private void click(Instant at, String referrer, String country, String device) {
		this.jdbc.update(
				"insert into click_events (link_id, occurred_at, referrer_host, country, device_type) values (?, ?, ?, ?, ?)",
				this.linkId, Timestamp.from(at), referrer, country, device);
	}

}
