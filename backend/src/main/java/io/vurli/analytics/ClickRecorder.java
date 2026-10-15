package io.vurli.analytics;

import java.time.Clock;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.vurli.analytics.internal.ClickWriter;
import io.vurli.analytics.internal.PendingClick;
import io.vurli.analytics.internal.UserAgents;

import org.springframework.stereotype.Service;

/**
 * Counts short link visits. Called on every redirect, so it only filters and queues: the
 * redirect never waits for the database (K22). Bots, link previews and prefetches are not
 * counted (product rule 6).
 */
@Service
public class ClickRecorder {

	private final ClickWriter writer;

	private final Clock clock;

	private final Counter ignored;

	public ClickRecorder(ClickWriter writer, Clock clock, MeterRegistry meters) {
		this.writer = writer;
		this.clock = clock;
		this.ignored = Counter.builder("vurli.clicks.ignored")
			.description("Visits not counted: bots, link previews, prefetches")
			.register(meters);
	}

	public void record(Visit visit) {
		if (visit.prefetch() || UserAgents.isBot(visit.userAgent())) {
			this.ignored.increment();
			return;
		}
		this.writer.offer(new PendingClick(visit.linkId(), this.clock.instant(), visit.userAgent(), visit.referer(),
				visit.address()));
	}

}
