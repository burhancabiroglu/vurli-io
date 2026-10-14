package io.vurli.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.vurli.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LinkLifecycleIntegrationTests {

	@Autowired
	private LinkManagement links;

	@Autowired
	private LinkModeration moderation;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID workspaceId;

	private UUID otherWorkspaceId;

	@BeforeEach
	void setUp() {
		this.workspaceId = createWorkspace();
		this.otherWorkspaceId = createWorkspace();
	}

	@Test
	void otherWorkspaceCannotSeeOrChangeLink() {
		LinkView theirs = this.links.create(this.otherWorkspaceId, CreateLinkRequest.of("https://example.com/theirs"));
		UUID id = theirs.id();

		assertThatExceptionOfType(LinkNotFoundException.class).isThrownBy(() -> this.links.get(this.workspaceId, id));
		assertThatExceptionOfType(LinkNotFoundException.class)
			.isThrownBy(() -> this.links.changeTarget(this.workspaceId, id, 0, "https://evil.com"));
		assertThatExceptionOfType(LinkNotFoundException.class)
			.isThrownBy(() -> this.links.changeExpiry(this.workspaceId, id, 0, null));
		assertThatExceptionOfType(LinkNotFoundException.class)
			.isThrownBy(() -> this.links.disable(this.workspaceId, id, 0));
		assertThatExceptionOfType(LinkNotFoundException.class)
			.isThrownBy(() -> this.links.enable(this.workspaceId, id, 0));
		assertThatExceptionOfType(LinkNotFoundException.class)
			.isThrownBy(() -> this.links.delete(this.workspaceId, id, 0));
		assertThat(this.links.list(this.workspaceId, 0, 100)).isEmpty();

		assertThat(this.links.get(this.otherWorkspaceId, id)).isEqualTo(theirs);
	}

	@Test
	void changeWithStaleVersionIsRejected() {
		LinkView link = create("https://example.com/v0");
		LinkView changed = this.links.changeTarget(this.workspaceId, link.id(), 0, "https://example.com/v1");

		assertThat(changed.version()).isEqualTo(1);
		assertThatExceptionOfType(LinkVersionConflictException.class)
			.isThrownBy(() -> this.links.changeTarget(this.workspaceId, link.id(), 0, "https://example.com/stale"));
		assertThat(this.links.get(this.workspaceId, link.id()).targetUrl()).isEqualTo("https://example.com/v1");
	}

	@Test
	void concurrentChangesWithSameVersionHaveExactlyOneWinner() throws Exception {
		LinkView link = create("https://example.com/v0");
		int writers = 8;
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Object>> results = new ArrayList<>();
		try (ExecutorService executor = Executors.newFixedThreadPool(writers)) {
			for (int i = 0; i < writers; i++) {
				String target = "https://example.com/writer-" + i;
				Callable<Object> write = () -> {
					start.await();
					try {
						return this.links.changeTarget(this.workspaceId, link.id(), 0, target);
					}
					catch (LinkVersionConflictException ex) {
						return ex;
					}
				};
				results.add(executor.submit(write));
			}
			start.countDown();

			List<Object> outcomes = new ArrayList<>();
			for (Future<Object> result : results) {
				outcomes.add(result.get(30, TimeUnit.SECONDS));
			}
			List<LinkView> winners = outcomes.stream()
				.filter(LinkView.class::isInstance)
				.map(LinkView.class::cast)
				.toList();
			assertThat(winners).hasSize(1);
			assertThat(outcomes).filteredOn(LinkVersionConflictException.class::isInstance).hasSize(writers - 1);
			LinkView stored = this.links.get(this.workspaceId, link.id());
			assertThat(stored.targetUrl()).isEqualTo(winners.getFirst().targetUrl());
			assertThat(stored.version()).isEqualTo(1);
		}
	}

	@Test
	void newTargetIsValidatedAndLosesSafetyCheck() {
		LinkView link = create("https://example.com/menu");
		this.jdbc.update("update links set safety_checked_at = now() where id = ?", link.id());

		assertThatExceptionOfType(InvalidTargetUrlException.class)
			.isThrownBy(() -> this.links.changeTarget(this.workspaceId, link.id(), 0, "javascript:alert(1)"));
		LinkView changed = this.links.changeTarget(this.workspaceId, link.id(), 0, "https://example.com/new");

		assertThat(changed.targetUrl()).isEqualTo("https://example.com/new");
		assertThat(this.jdbc.queryForObject("select safety_checked_at from links where id = ?", Instant.class,
				link.id()))
			.isNull();
	}

	@Test
	void expiryCanBeSetAndRemovedButNotInThePast() {
		LinkView link = create("https://example.com/menu");
		Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);

		assertThatExceptionOfType(InvalidExpiryException.class).isThrownBy(
				() -> this.links.changeExpiry(this.workspaceId, link.id(), 0, Instant.now().minusSeconds(1)));
		LinkView withExpiry = this.links.changeExpiry(this.workspaceId, link.id(), 0, tomorrow);
		LinkView withoutExpiry = this.links.changeExpiry(this.workspaceId, link.id(), withExpiry.version(), null);

		assertThat(withExpiry.expiresAt()).isEqualTo(tomorrow);
		assertThat(withoutExpiry.expiresAt()).isNull();
	}

	@Test
	void ownerCanDisableAndEnable() {
		LinkView link = create("https://example.com/menu");

		LinkView disabled = this.links.disable(this.workspaceId, link.id(), 0);
		LinkView enabled = this.links.enable(this.workspaceId, link.id(), disabled.version());

		assertThat(disabled.status()).isEqualTo(LinkStatus.DISABLED);
		assertThat(enabled.status()).isEqualTo(LinkStatus.ACTIVE);
	}

	@Test
	void ownerCannotLiftOrWorkAroundABlock() {
		LinkView link = create("https://example.com/menu");
		this.moderation.block(link.id());
		long version = this.links.get(this.workspaceId, link.id()).version();

		assertThatExceptionOfType(LinkBlockedException.class)
			.isThrownBy(() -> this.links.enable(this.workspaceId, link.id(), version));
		assertThatExceptionOfType(LinkBlockedException.class)
			.isThrownBy(() -> this.links.disable(this.workspaceId, link.id(), version));
		assertThatExceptionOfType(LinkBlockedException.class)
			.isThrownBy(() -> this.links.changeTarget(this.workspaceId, link.id(), version, "https://example.com/x"));
		assertThatExceptionOfType(LinkBlockedException.class)
			.isThrownBy(() -> this.links.changeExpiry(this.workspaceId, link.id(), version, null));
		assertThat(this.links.get(this.workspaceId, link.id()).status()).isEqualTo(LinkStatus.BLOCKED);
	}

	@Test
	void blockWinsOverOwnerVersion() {
		LinkView link = create("https://example.com/menu");
		LinkView changed = this.links.changeTarget(this.workspaceId, link.id(), 0, "https://example.com/new");

		this.moderation.block(link.id());

		assertThat(this.links.get(this.workspaceId, link.id()).status()).isEqualTo(LinkStatus.BLOCKED);
		assertThatExceptionOfType(LinkVersionConflictException.class)
			.isThrownBy(() -> this.links.disable(this.workspaceId, link.id(), changed.version()));
	}

	@Test
	void ownerCanDeleteBlockedLink() {
		LinkView link = create("https://example.com/menu");
		this.moderation.block(link.id());
		long version = this.links.get(this.workspaceId, link.id()).version();

		this.links.delete(this.workspaceId, link.id(), version);

		assertThatExceptionOfType(LinkNotFoundException.class)
			.isThrownBy(() -> this.links.get(this.workspaceId, link.id()));
	}

	@Test
	void deletedLinkDisappearsForOwner() {
		LinkView link = create("https://example.com/menu");

		this.links.delete(this.workspaceId, link.id(), 0);

		assertThatExceptionOfType(LinkNotFoundException.class)
			.isThrownBy(() -> this.links.get(this.workspaceId, link.id()));
		assertThatExceptionOfType(LinkNotFoundException.class)
			.isThrownBy(() -> this.links.delete(this.workspaceId, link.id(), 1));
		assertThat(this.links.list(this.workspaceId, 0, 100)).isEmpty();
	}

	@Test
	void listIsNewestFirstAndPaged() {
		List<UUID> created = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			UUID id = create("https://example.com/" + i).id();
			// Distinct timestamps: consecutive inserts can share the same microsecond.
			this.jdbc.update("update links set created_at = now() - make_interval(mins => ?) where id = ?", 10 - i,
					id);
			created.add(id);
		}

		assertThat(this.links.list(this.workspaceId, 0, 10)).extracting(LinkView::id)
			.containsExactly(created.get(2), created.get(1), created.get(0));
		assertThat(this.links.list(this.workspaceId, 0, 2)).hasSize(2);
		assertThat(this.links.list(this.workspaceId, 1, 2)).extracting(LinkView::id).containsExactly(created.get(0));
	}

	@Test
	void pageSizeIsCapped() {
		for (int i = 0; i <= LinkManagement.MAX_PAGE_SIZE; i++) {
			create("https://example.com/" + i);
		}

		assertThat(this.links.list(this.workspaceId, 0, 10_000)).hasSize(LinkManagement.MAX_PAGE_SIZE);
	}

	@Test
	void unknownLinkIsNotFound() {
		assertThatExceptionOfType(LinkNotFoundException.class)
			.isThrownBy(() -> this.moderation.block(UUID.randomUUID()));
	}

	private LinkView create(String targetUrl) {
		return this.links.create(this.workspaceId, CreateLinkRequest.of(targetUrl));
	}

	private UUID createWorkspace() {
		UUID id = UUID.randomUUID();
		this.jdbc.update("insert into workspaces (id, name, created_at) values (?, 'test', now())", id);
		return id;
	}

}
