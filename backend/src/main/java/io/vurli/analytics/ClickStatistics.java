package io.vurli.analytics;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads statistics from daily summaries plus the raw clicks of days not summarized yet
 * (today, and any day the nightly job has not reached). A day is counted from exactly one
 * of the two sources.
 * <p>
 * Callers must check that the link belongs to the caller's workspace first.
 */
@Service
public class ClickStatistics {

	static final int WINDOW_DAYS = 30;

	static final int TOP = 10;

	/** Raw clicks of this link on days without a summary row. */
	private static final String LIVE = """
			with live as (
			    select (e.occurred_at at time zone 'UTC')::date as day, e.referrer_host, e.country, e.device_type
			    from click_events e
			    where e.link_id = :link
			      and not exists (select 1 from link_daily_stats s
			                      where s.link_id = :link and s.day = (e.occurred_at at time zone 'UTC')::date)
			)
			""";

	private static final String TOTAL = LIVE + """
			select (select coalesce(sum(clicks), 0) from link_daily_stats where link_id = :link)
			     + (select count(*) from live)""";

	private static final String DAYS = LIVE + """
			select day, sum(clicks) as clicks from (
			    select day, clicks from link_daily_stats where link_id = :link and day >= :from
			    union all
			    select day, count(*) from live where day >= :from group by day
			) d group by day""";

	private final NamedParameterJdbcTemplate jdbc;

	private final Clock clock;

	public ClickStatistics(NamedParameterJdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** One snapshot, so totals, days and breakdowns agree with each other. */
	@Transactional(readOnly = true)
	public LinkStatistics forLink(UUID linkId) {
		LocalDate today = LocalDate.now(this.clock.withZone(ZoneOffset.UTC));
		LocalDate from = today.minusDays(WINDOW_DAYS - 1);
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("link", linkId).addValue("from", from);

		Long total = this.jdbc.queryForObject(TOTAL, params, Long.class);
		Map<LocalDate, Long> byDay = new HashMap<>();
		this.jdbc.query(DAYS, params, (RowCallbackHandler) (row) -> byDay.put(row.getObject("day", LocalDate.class),
				row.getLong("clicks")));
		List<LinkStatistics.DailyClicks> days = new ArrayList<>(WINDOW_DAYS);
		long windowClicks = 0;
		for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
			long clicks = byDay.getOrDefault(day, 0L);
			days.add(new LinkStatistics.DailyClicks(day, clicks));
			windowClicks += clicks;
		}
		return new LinkStatistics((total != null) ? total : 0, windowClicks, from, today, days,
				breakdown("by_referrer", "referrer_host", params), breakdown("by_country", "country", params),
				breakdown("by_device", "device_type", params));
	}

	/** Column names come from the fixed arguments above, never from a request. */
	private List<LinkStatistics.Share> breakdown(String summaryColumn, String rawColumn, MapSqlParameterSource params) {
		String sql = LIVE + """
				select key, sum(n) as clicks from (
				    select b.key, b.value::bigint as n
				    from link_daily_stats s, jsonb_each_text(s.%1$s) b
				    where s.link_id = :link and s.day >= :from
				    union all
				    select coalesce(%2$s, ''), count(*) from live where day >= :from group by 1
				) x group by key order by clicks desc, key limit %3$d""".formatted(summaryColumn, rawColumn, TOP);
		return this.jdbc.query(sql, params, (row, i) -> {
			String key = row.getString("key");
			return new LinkStatistics.Share(key.isEmpty() ? null : key, row.getLong("clicks"));
		});
	}

}
