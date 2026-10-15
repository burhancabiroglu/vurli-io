package io.vurli.abuse;

import java.sql.Timestamp;
import java.time.Clock;
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

}
