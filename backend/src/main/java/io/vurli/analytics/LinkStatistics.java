package io.vurli.analytics;

import java.time.LocalDate;
import java.util.List;

/**
 * Clicks of one link. Days are UTC days (K45); the window is the last 30 including today.
 * @param totalClicks since the link was created
 * @param windowClicks within the window; breakdowns list only the largest entries, the
 * rest is {@code windowClicks} minus their sum
 * @param days one entry per day of the window, oldest first, zero-filled
 * @param referrers largest first; a {@code null} key means no referrer
 * @param countries ISO 3166 codes, largest first; a {@code null} key means unknown
 * @param devices largest first
 */
public record LinkStatistics(long totalClicks, long windowClicks, LocalDate from, LocalDate to, List<DailyClicks> days,
		List<Share> referrers, List<Share> countries, List<Share> devices) {

	public record DailyClicks(LocalDate day, long clicks) {
	}

	public record Share(String key, long clicks) {
	}

}
