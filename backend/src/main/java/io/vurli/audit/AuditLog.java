package io.vurli.audit;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Append-only record of administrator and system actions: promotions, second-factor
 * changes, sign-ins and, from M3 slice 3b, blocks. Details must never hold secrets,
 * codes or passwords.
 */
@Service
public class AuditLog {

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final Clock clock;

	public AuditLog(JdbcClient jdbc, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	/**
	 * @param actorId the administrator, or {@code null} for the system itself
	 */
	public void record(UUID actorId, String action, String targetType, String targetId, Map<String, ?> details) {
		this.jdbc.sql("""
				insert into audit_log (actor_id, action, target_type, target_id, details, created_at)
				values (?, ?, ?, ?, ?::jsonb, ?)""")
			.params(actorId, action, targetType, targetId, this.json.writeValueAsString(details),
					Timestamp.from(this.clock.instant()))
			.update();
	}

	/** The newest entries first. */
	public List<AuditEntry> recent(int limit) {
		return this.jdbc.sql("""
				select id, actor_id, action, target_type, target_id, details::text as details, created_at
				from audit_log order by created_at desc, id desc limit ?""")
			.param(Math.clamp(limit, 1, 500))
			.query((row, i) -> new AuditEntry(row.getLong("id"), row.getObject("actor_id", UUID.class),
					row.getString("action"), row.getString("target_type"), row.getString("target_id"),
					row.getString("details"), row.getTimestamp("created_at").toInstant()))
			.list();
	}

}
