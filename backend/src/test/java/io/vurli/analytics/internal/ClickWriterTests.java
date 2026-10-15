package io.vurli.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class ClickWriterTests {

	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

	@Test
	void aFullQueueDropsInsteadOfBlocking() {
		ClickWriter writer = new ClickWriter(2, this.jdbc, CountryLookup.NONE, this.meters);

		assertThat(writer.offer(click())).isTrue();
		assertThat(writer.offer(click())).isTrue();
		assertThat(writer.offer(click())).isFalse();
		assertThat(this.meters.counter("vurli.clicks.dropped").count()).isEqualTo(1);
	}

	@Test
	void aFailedWriteIsCountedAndDoesNotThrow() {
		given(this.jdbc.batchUpdate(anyString(), anyList())).willThrow(new DataAccessResourceFailureException("down"));
		ClickWriter writer = new ClickWriter(10, this.jdbc, CountryLookup.NONE, this.meters);
		writer.offer(click());
		writer.offer(click());

		writer.flush();

		assertThat(this.meters.counter("vurli.clicks.dropped").count()).isEqualTo(2);
		assertThat(this.meters.counter("vurli.clicks.recorded").count()).isZero();
	}

	private static PendingClick click() {
		return new PendingClick(UUID.randomUUID(), Instant.now(), "Mozilla/5.0 (Windows NT 10.0)", null, null);
	}

}
