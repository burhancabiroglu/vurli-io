package io.vurli.abuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import io.vurli.ClientHeaderFilter;
import io.vurli.Mailpit;
import io.vurli.TestAccounts;
import io.vurli.TestcontainersConfiguration;
import io.vurli.abuse.internal.SafetyScanner;
import io.vurli.account.AccountManagement;
import io.vurli.link.CreateLinkRequest;
import io.vurli.link.LinkManagement;
import io.vurli.link.LinkModeration;
import io.vurli.link.LinkStatus;
import io.vurli.link.LinkView;
import io.vurli.link.TargetSafety;
import io.vurli.link.TargetSafety.Verdict;
import io.vurli.link.UnsafeTargetException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Safety checks at creation and in the background scan, with the Web Risk answer under
 * the test's control: targets containing "phish" are listed, "outage" gets no answer.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SafetyIntegrationTests {

	@MockitoBean
	private TargetSafety safety;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private LinkManagement links;

	@Autowired
	private LinkModeration moderation;

	@Autowired
	private SafetyScanner scanner;

	@Autowired
	private AccountManagement accounts;

	@Autowired
	private Mailpit mailpit;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID workspaceId;

	@BeforeEach
	void listedTargets() {
		given(this.safety.check(anyString())).willAnswer((invocation) -> {
			String target = invocation.getArgument(0);
			return target.contains("phish") ? Verdict.UNSAFE : target.contains("outage") ? Verdict.UNKNOWN : Verdict.SAFE;
		});
		this.workspaceId = UUID.randomUUID();
		this.jdbc.update("insert into workspaces (id, name, created_at) values (?, 'test', now())", this.workspaceId);
	}

	@Test
	void aListedTargetIsRefusedAndNothingIsStored() throws Exception {
		assertThatExceptionOfType(UnsafeTargetException.class).isThrownBy(
				() -> this.links.create(this.workspaceId, new CreateLinkRequest("https://phish.example/login", "kurban-" + suffix(), null)));
		assertThat(this.jdbc.queryForObject("select count(*) from links where workspace_id = ?", Integer.class,
				this.workspaceId)).isZero();

		Cookie session = TestAccounts.login(this.mvc, TestAccounts.registerVerified(this.accounts, this.mailpit),
				TestAccounts.PASSWORD);
		this.mvc.perform(post("/api/v1/links").header(ClientHeaderFilter.HEADER, "test")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"targetUrl\": \"https://phish.example/login\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("unsafe_target"));
	}

	@Test
	void theCheckIsRecordedOnlyWhenThereWasAnAnswer() {
		LinkView checked = create("https://example.com/ok");
		LinkView unchecked = create("https://outage.example.com/");

		assertThat(checkedAt(checked.id())).isNotNull();
		assertThat(checkedAt(unchecked.id())).isNull();
	}

	@Test
	void aNewTargetIsCheckedToo() {
		LinkView link = create("https://example.com/ok");

		assertThatExceptionOfType(UnsafeTargetException.class)
			.isThrownBy(() -> this.links.changeTarget(this.workspaceId, link.id(), link.version(), "https://phish.example/"));
		assertThat(this.links.get(this.workspaceId, link.id()).targetUrl()).isEqualTo("https://example.com/ok");

		LinkView changed = this.links.changeTarget(this.workspaceId, link.id(), link.version(), "https://outage.example.com/");
		assertThat(changed.targetUrl()).isEqualTo("https://outage.example.com/");
		assertThat(checkedAt(link.id())).isNull();
	}

	@Test
	void theScanBlocksATargetThatTurnedDangerousAndRecordsIt() throws Exception {
		LinkView link = create("https://example.com/later-bad");
		// Found safe eight days ago; since then the site has turned bad.
		given(this.safety.check("https://example.com/later-bad")).willReturn(Verdict.UNSAFE);
		this.jdbc.update("update links set safety_checked_at = ? where id = ?",
				Timestamp.from(Instant.now().minus(Duration.ofDays(8))), link.id());
		this.mvc.perform(get("http://vurli.io/" + link.code())).andExpect(status().isFound());
		onlyDue(link.id());

		this.scanner.scan();

		this.mvc.perform(get("http://vurli.io/" + link.code())).andExpect(status().isGone());
		assertThat(this.jdbc.queryForObject(
				"select count(*) from audit_log where action = 'LINK_BLOCKED_BY_SCAN' and target_id = ? and actor_id is null",
				Integer.class, link.id().toString())).isEqualTo(1);
	}

	@Test
	void theScanChecksUncheckedLinksAndLeavesRecentChecksAlone() {
		given(this.safety.check(anyString())).willReturn(Verdict.UNKNOWN);
		LinkView unchecked = create("https://example.com/unchecked");
		given(this.safety.check(anyString())).willReturn(Verdict.SAFE);
		LinkView recent = create("https://example.com/recent");
		Instant recentCheck = checkedAt(recent.id());
		onlyDue(unchecked.id(), recent.id());

		this.scanner.scan();

		assertThat(checkedAt(unchecked.id())).isNotNull();
		assertThat(checkedAt(recent.id())).isEqualTo(recentCheck);
	}

	@Test
	void theScanStopsWithoutAnAnswerAndChangesNothing() {
		given(this.safety.check(anyString())).willReturn(Verdict.UNKNOWN);
		LinkView first = create("https://example.com/a");
		LinkView second = create("https://example.com/b");
		LinkView third = create("https://example.com/c");
		LinkView fourth = create("https://example.com/d");
		onlyDue(first.id(), second.id(), third.id(), fourth.id());
		given(this.safety.check(anyString())).willReturn(Verdict.UNKNOWN, Verdict.UNKNOWN, Verdict.UNKNOWN, Verdict.SAFE);
		clearInvocations(this.safety);

		this.scanner.scan();

		// Three missing answers in a row: an outage, the fourth is not even asked.
		verify(this.safety, times(3)).check(anyString());
		assertThat(checkedAt(fourth.id())).isNull();
		assertThat(this.links.get(this.workspaceId, first.id()).status()).isEqualTo(LinkStatus.ACTIVE);
	}

	@Test
	void oneUrlWithoutAnAnswerDoesNotHoldUpTheOthers() {
		LinkView stuck = create("https://outage.example.com/always");
		LinkView next = create("https://example.com/next");
		onlyDue(stuck.id(), next.id());
		this.jdbc.update("update links set safety_checked_at = null where id in (?, ?)", stuck.id(), next.id());

		this.scanner.scan();

		assertThat(checkedAt(stuck.id())).isNull();
		assertThat(checkedAt(next.id())).isNotNull();
	}

	@Test
	void aVerdictForAnOldTargetIsVoid() {
		LinkView link = create("https://example.com/old");
		this.links.changeTarget(this.workspaceId, link.id(), link.version(), "https://example.com/new");

		// The scan read the old target, then the owner replaced it.
		assertThat(this.moderation.blockIfTarget(link.id(), "https://example.com/old")).isFalse();
		assertThat(this.moderation.recordSafe(link.id(), "https://example.com/old")).isFalse();
		assertThat(this.links.get(this.workspaceId, link.id()).status()).isEqualTo(LinkStatus.ACTIVE);
	}

	/**
	 * Marks every other link as freshly checked, so links left by other tests do not get in
	 * the way. The given links keep their state.
	 */
	private void onlyDue(UUID... ids) {
		String placeholders = String.join(", ", Collections.nCopies(ids.length, "?"));
		this.jdbc.update("update links set safety_checked_at = now() where id not in (" + placeholders + ")",
				(Object[]) ids);
	}

	private LinkView create(String target) {
		return this.links.create(this.workspaceId, new CreateLinkRequest(target, "safety-" + suffix(), null));
	}

	private Instant checkedAt(UUID linkId) {
		Timestamp at = this.jdbc.queryForObject("select safety_checked_at from links where id = ?", Timestamp.class,
				linkId);
		return (at != null) ? at.toInstant() : null;
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
