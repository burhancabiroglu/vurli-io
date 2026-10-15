package io.vurli.analytics.internal;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * A bounded in-memory queue of clicks, written in batches every second (K22). A full queue
 * drops clicks instead of slowing redirects down; so does a failed write. Both are counted
 * in {@code vurli.clicks.dropped}. A crash loses at most the clicks of the last second.
 */
public class ClickWriter {

	private static final Logger log = LoggerFactory.getLogger(ClickWriter.class);

	static final int BATCH_SIZE = 500;

	private static final String INSERT = """
			insert into click_events (link_id, occurred_at, referrer_host, country, device_type)
			values (?, ?, ?, ?, ?)""";

	private final BlockingQueue<PendingClick> queue;

	private final JdbcTemplate jdbc;

	private final CountryLookup countries;

	private final Counter recorded;

	private final Counter dropped;

	public ClickWriter(int capacity, JdbcTemplate jdbc, CountryLookup countries, MeterRegistry meters) {
		this.queue = new ArrayBlockingQueue<>(capacity);
		this.jdbc = jdbc;
		this.countries = countries;
		this.recorded = Counter.builder("vurli.clicks.recorded").register(meters);
		this.dropped = Counter.builder("vurli.clicks.dropped")
			.description("Clicks lost to a full queue or a failed write")
			.register(meters);
	}

	/** Never blocks. */
	public boolean offer(PendingClick click) {
		boolean queued = this.queue.offer(click);
		if (!queued) {
			this.dropped.increment();
		}
		return queued;
	}

	@Scheduled(fixedDelayString = "${vurli.analytics.flush-interval:1s}")
	public void flush() {
		List<PendingClick> batch = new ArrayList<>(BATCH_SIZE);
		while (this.queue.drainTo(batch, BATCH_SIZE) > 0) {
			write(batch);
			batch.clear();
		}
	}

	@PreDestroy
	void shutdown() {
		flush();
	}

	private void write(List<PendingClick> batch) {
		List<Object[]> rows = new ArrayList<>(batch.size());
		for (PendingClick click : batch) {
			rows.add(new Object[] { click.linkId(), Timestamp.from(click.occurredAt()),
					ReferrerHosts.hostOf(click.referer()), this.countries.country(click.address()),
					UserAgents.deviceType(click.userAgent()).name() });
		}
		try {
			this.jdbc.batchUpdate(INSERT, rows);
			this.recorded.increment(batch.size());
		}
		catch (DataAccessException ex) {
			// Statistics are approximate (product rule 6); a redirect never fails for them.
			this.dropped.increment(batch.size());
			log.warn("Dropped {} clicks: {}", batch.size(), ex.getMessage());
		}
	}

}
