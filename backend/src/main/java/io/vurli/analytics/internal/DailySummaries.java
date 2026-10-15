package io.vurli.analytics.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns raw clicks of completed UTC days (K45) into one row per link and day, then
 * deletes raw clicks past their retention (K22). Days are only summarized once and the
 * job can run any number of times; a missed night is caught up the next time.
 */
@Component
public class DailySummaries {

	private static final Logger log = LoggerFactory.getLogger(DailySummaries.class);

	/** Every completed day with raw clicks and no summary yet. Unknown keys become "". */
	private static final String SUMMARIZE = """
			with clicks as (
			    select e.link_id, (e.occurred_at at time zone 'UTC')::date as day,
			           coalesce(e.referrer_host, '') as referrer, coalesce(e.country, '') as country, e.device_type
			    from click_events e
			    where e.occurred_at < ?
			      and not exists (select 1 from link_daily_stats s
			                      where s.link_id = e.link_id and s.day = (e.occurred_at at time zone 'UTC')::date)
			),
			totals as (select link_id, day, count(*) as clicks from clicks group by link_id, day),
			referrers as (select link_id, day, jsonb_object_agg(referrer, n) as counts
			              from (select link_id, day, referrer, count(*) as n from clicks group by 1, 2, 3) r
			              group by link_id, day),
			countries as (select link_id, day, jsonb_object_agg(country, n) as counts
			              from (select link_id, day, country, count(*) as n from clicks group by 1, 2, 3) c
			              group by link_id, day),
			devices as (select link_id, day, jsonb_object_agg(device_type, n) as counts
			            from (select link_id, day, device_type, count(*) as n from clicks group by 1, 2, 3) d
			            group by link_id, day)
			insert into link_daily_stats (link_id, day, clicks, by_referrer, by_country, by_device)
			select t.link_id, t.day, t.clicks, r.counts, c.counts, d.counts
			from totals t
			join referrers r using (link_id, day)
			join countries c using (link_id, day)
			join devices d using (link_id, day)
			on conflict (link_id, day) do nothing""";

	private final JdbcTemplate jdbc;

	private final AnalyticsProperties properties;

	private final Clock clock;

	public DailySummaries(JdbcTemplate jdbc, AnalyticsProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.properties = properties;
		this.clock = clock;
	}

	/** Ten past midnight UTC, when the last clicks of the day have left the queue. */
	@Scheduled(cron = "0 10 0 * * *", zone = "UTC")
	public void nightly() {
		run(LocalDate.now(this.clock.withZone(ZoneOffset.UTC)));
	}

	/** Summarizes before deleting, so no click is deleted without being counted. */
	@Transactional
	public void run(LocalDate today) {
		Instant startOfToday = today.atStartOfDay(ZoneOffset.UTC).toInstant();
		int summarized = this.jdbc.update(SUMMARIZE, Timestamp.from(startOfToday));
		int deleted = this.jdbc.update("delete from click_events where occurred_at < ?",
				Timestamp.from(startOfToday.minus(this.properties.retention())));
		log.info("Daily statistics: {} link-days summarized, {} raw clicks past retention deleted", summarized,
				deleted);
	}

}
