package io.vurli.abuse;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import io.vurli.link.LinkModeration;
import io.vurli.link.ModeratedLink;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Reports of short links from the public form (K51). Whether the reported code exists is
 * never revealed to the reporter; administrators see and resolve the reports.
 */
@Service
public class AbuseReports {

	private static final Pattern CODE = Pattern.compile("[0-9A-Za-z-]{3,50}");

	private static final Pattern EMAIL = Pattern.compile("[^\\s@]{1,64}@[^\\s@]{1,189}\\.[^\\s@]{2,}");

	static final int MAX_DETAILS = 2000;

	private final JdbcClient jdbc;

	private final LinkModeration links;

	private final Clock clock;

	public AbuseReports(JdbcClient jdbc, LinkModeration links, Clock clock) {
		this.jdbc = jdbc;
		this.links = links;
		this.clock = clock;
	}

	/**
	 * @param link a short link as the reporter has it: {@code https://vurli.io/abc},
	 * {@code vurli.io/abc} or just {@code abc}
	 * @throws InvalidReportException if the link, details or email cannot be used
	 */
	public void submit(String link, ReportReason reason, String details, String reporterEmail) {
		String code = codeOf(link);
		String text = (details == null) ? "" : details.strip();
		if (text.length() > MAX_DETAILS) {
			throw new InvalidReportException(InvalidReportException.Field.DETAILS);
		}
		String email = (reporterEmail == null || reporterEmail.isBlank()) ? null
				: reporterEmail.strip().toLowerCase(Locale.ROOT);
		if (email != null && (email.length() > 254 || !EMAIL.matcher(email).matches())) {
			throw new InvalidReportException(InvalidReportException.Field.EMAIL);
		}
		UUID linkId = this.links.findByCode(code).map(ModeratedLink::id).orElse(null);
		this.jdbc.sql("""
				insert into abuse_reports (id, code, link_id, reason, details, reporter_email, created_at)
				values (?, ?, ?, ?, ?, ?, ?)""")
			.params(UUID.randomUUID(), code, linkId, reason.name(), text, email, Timestamp.from(this.clock.instant()))
			.update();
	}

	/** Open reports, newest first. */
	public List<AbuseReport> open(int limit) {
		return this.jdbc.sql("""
				select id, code, link_id, reason, details, reporter_email, created_at from abuse_reports
				where status = 'OPEN' order by created_at desc limit ?""")
			.param(Math.clamp(limit, 1, 500))
			.query(AbuseReports::report)
			.list();
	}

	public long openCount() {
		return this.jdbc.sql("select count(*) from abuse_reports where status = 'OPEN'").query(Long.class).single();
	}

	/** @return false if it was already resolved or does not exist */
	public boolean resolve(UUID reportId, UUID administratorId) {
		return this.jdbc.sql("""
				update abuse_reports set status = 'RESOLVED', resolved_at = ?, resolved_by = ?
				where id = ? and status = 'OPEN'""")
			.params(Timestamp.from(this.clock.instant()), administratorId, reportId)
			.update() == 1;
	}

	/** An open report. */
	public Optional<AbuseReport> get(UUID reportId) {
		return this.jdbc.sql("""
				select id, code, link_id, reason, details, reporter_email, created_at from abuse_reports
				where id = ? and status = 'OPEN'""")
			.param(reportId)
			.query(AbuseReports::report)
			.optional();
	}

	private static AbuseReport report(ResultSet row, int index) throws SQLException {
		return new AbuseReport(row.getObject("id", UUID.class), row.getString("code"),
				row.getObject("link_id", UUID.class), ReportReason.valueOf(row.getString("reason")),
				row.getString("details"), row.getString("reporter_email"), row.getTimestamp("created_at").toInstant());
	}

	/** The code from what a reporter pasted: the last path segment of a URL, or the text. */
	static String codeOf(String link) {
		String text = (link == null) ? "" : link.strip();
		if (text.contains("/")) {
			try {
				String path = new URI(text.contains("://") ? text : "https://" + text).getPath();
				text = (path == null) ? "" : path.replaceAll("^/+|/+$", "");
			}
			catch (URISyntaxException ex) {
				throw new InvalidReportException(InvalidReportException.Field.LINK);
			}
		}
		if (!CODE.matcher(text).matches()) {
			throw new InvalidReportException(InvalidReportException.Field.LINK);
		}
		return text;
	}

}
