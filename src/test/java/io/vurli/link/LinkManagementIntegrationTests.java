package io.vurli.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.vurli.TestcontainersConfiguration;
import io.vurli.link.internal.CodeGenerator;
import io.vurli.link.internal.LinkCache;
import io.vurli.link.internal.LinkRepository;
import io.vurli.link.internal.TargetUrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LinkManagementIntegrationTests {

	private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

	@Autowired
	private LinkManagement linkManagement;

	@Autowired
	private LinkRepository linkRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private TargetUrlValidator targetUrlValidator;

	@Autowired
	private LinkCache linkCache;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID workspaceId;

	@BeforeEach
	void setUp() {
		this.jdbc.update("truncate table links, workspaces");
		this.workspaceId = UUID.randomUUID();
		this.jdbc.update("insert into workspaces (id, name, created_at) values (?, 'test', now())", this.workspaceId);
	}

	@Test
	void createsLinkWithRandomCode() {
		LinkView link = this.linkManagement.create(this.workspaceId, CreateLinkRequest.of("HTTPS://Example.com/menu"));

		assertThat(link.code()).matches("[0-9A-Za-z]{7}");
		assertThat(link.targetUrl()).isEqualTo("https://example.com/menu");
		assertThat(link.status()).isEqualTo(LinkStatus.ACTIVE);
		assertThat(link.version()).isZero();
		assertThat(countLinksWithCode(link.code())).isOne();
	}

	@Test
	void createsLinkWithLowercasedAlias() {
		LinkView link = this.linkManagement.create(this.workspaceId,
				new CreateLinkRequest("https://example.com", "Kafe-Menu", null));

		assertThat(link.code()).isEqualTo("kafe-menu");
	}

	@Test
	void rejectsAliasThatDiffersOnlyInCase() {
		create("kafe-menu");

		assertThatExceptionOfType(AliasTakenException.class).isThrownBy(() -> create("KAFE-MENU"));
	}

	@Test
	void concurrentRequestsForSameAliasHaveExactlyOneWinner() throws Exception {
		int requests = 8;
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Object>> results = new ArrayList<>();
		try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
			for (int i = 0; i < requests; i++) {
				Callable<Object> attempt = () -> {
					start.await();
					try {
						return create("yaz-kampanyasi");
					}
					catch (AliasTakenException ex) {
						return ex;
					}
				};
				results.add(executor.submit(attempt));
			}
			start.countDown();

			List<Object> outcomes = new ArrayList<>();
			for (Future<Object> result : results) {
				outcomes.add(result.get(30, TimeUnit.SECONDS));
			}
			assertThat(outcomes).filteredOn(LinkView.class::isInstance).hasSize(1);
			assertThat(outcomes).filteredOn(AliasTakenException.class::isInstance).hasSize(requests - 1);
		}
		assertThat(countLinksWithCode("yaz-kampanyasi")).isOne();
	}

	@Test
	void aliasOfDeletedLinkIsNeverReused() {
		create("eski-menu");
		this.jdbc.update("update links set deleted_at = now() where code = 'eski-menu'");

		assertThatExceptionOfType(AliasTakenException.class).isThrownBy(() -> create("eski-menu"));
	}

	@Test
	void retriesWhenGeneratedCodeIsTaken() {
		create("taken01");
		LinkManagement management = withCodes("taken01", "fresh01");

		LinkView link = management.create(this.workspaceId, CreateLinkRequest.of("https://example.com"));

		assertThat(link.code()).isEqualTo("fresh01");
	}

	@Test
	void skipsGeneratedCodeThatIsReserved() {
		LinkManagement management = withCodes("support", "fresh02");

		LinkView link = management.create(this.workspaceId, CreateLinkRequest.of("https://example.com"));

		assertThat(link.code()).isEqualTo("fresh02");
	}

	@Test
	void givesUpAfterThreeCollisions() {
		create("taken02");
		LinkManagement management = withCodes("taken02", "taken02", "taken02", "fresh03");

		assertThatIllegalStateException()
			.isThrownBy(() -> management.create(this.workspaceId, CreateLinkRequest.of("https://example.com")));
		assertThat(this.linkRepository.count()).isOne();
	}

	@Test
	void requiresExpiryInTheFuture() {
		LinkManagement management = withCodes("expire1", "expire2");

		assertThatExceptionOfType(InvalidExpiryException.class)
			.isThrownBy(() -> management.create(this.workspaceId, new CreateLinkRequest("https://example.com", null, NOW)));
		LinkView link = management.create(this.workspaceId,
				new CreateLinkRequest("https://example.com", null, NOW.plus(Duration.ofSeconds(1))));

		assertThat(link.expiresAt()).isEqualTo(NOW.plusSeconds(1));
	}

	@Test
	void invalidRequestsWriteNothing() {
		assertThatExceptionOfType(InvalidTargetUrlException.class)
			.isThrownBy(() -> create("https://vurli.io/loop", "loop"));
		assertThatExceptionOfType(InvalidAliasException.class).isThrownBy(() -> create("admin"));

		assertThat(this.linkRepository.count()).isZero();
	}

	private LinkView create(String alias) {
		return create("https://example.com", alias);
	}

	private LinkView create(String targetUrl, String alias) {
		return this.linkManagement.create(this.workspaceId, new CreateLinkRequest(targetUrl, alias, null));
	}

	/** A {@link LinkManagement} whose generator returns the given codes in order, at a fixed time. */
	private LinkManagement withCodes(String... codes) {
		Deque<String> queue = new ArrayDeque<>(List.of(codes));
		CodeGenerator generator = queue::removeFirst;
		return new LinkManagement(this.linkRepository, this.transactionManager, this.targetUrlValidator, generator,
				this.linkCache, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private int countLinksWithCode(String code) {
		return this.jdbc.queryForObject("select count(*) from links where code = ?", Integer.class, code);
	}

}
