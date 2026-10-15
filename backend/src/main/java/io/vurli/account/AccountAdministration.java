package io.vurli.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator actions on accounts. Only the admin panel calls this; it checks the caller
 * and writes the audit log.
 */
@Service
public class AccountAdministration {

	private static final String SUMMARY = """
			select u.id, u.email, u.role, u.email_verified_at, u.suspended_at, u.created_at,
			       (select w.id from workspaces w where w.owner_id = u.id order by w.created_at limit 1) as workspace_id
			from users u""";

	private final JdbcClient jdbc;

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	private final Clock clock;

	public AccountAdministration(JdbcClient jdbc, FindByIndexNameSessionRepository<? extends Session> sessions,
			Clock clock) {
		this.jdbc = jdbc;
		this.sessions = sessions;
		this.clock = clock;
	}

	/** Accounts whose email contains the text, newest first. */
	public List<AccountSummary> search(String query, int limit) {
		String text = (query == null) ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (text.isEmpty()) {
			return List.of();
		}
		String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		return this.jdbc.sql(SUMMARY + " where u.email like :pattern escape '\\' order by u.created_at desc limit :limit")
			.param("pattern", pattern)
			.param("limit", Math.clamp(limit, 1, 100))
			.query(AccountAdministration::summary)
			.list();
	}

	public Optional<AccountSummary> get(UUID userId) {
		return this.jdbc.sql(SUMMARY + " where u.id = :id").param("id", userId).query(AccountAdministration::summary).optional();
	}

	/** The owner's email of each workspace, for showing whose a link is. */
	public Map<UUID, String> ownerEmails(Collection<UUID> workspaceIds) {
		Map<UUID, String> emails = new HashMap<>();
		if (!workspaceIds.isEmpty()) {
			this.jdbc.sql("select w.id, u.email from workspaces w join users u on u.id = w.owner_id where w.id in (:ids)")
				.param("ids", List.copyOf(workspaceIds))
				.query((row) -> {
					emails.put(row.getObject("id", UUID.class), row.getString("email"));
				});
		}
		return emails;
	}

	public Map<UUID, String> emails(Collection<UUID> userIds) {
		Map<UUID, String> emails = new HashMap<>();
		if (!userIds.isEmpty()) {
			this.jdbc.sql("select id, email from users where id in (:ids)")
				.param("ids", List.copyOf(userIds))
				.query((row) -> {
					emails.put(row.getObject("id", UUID.class), row.getString("email"));
				});
		}
		return emails;
	}

	/**
	 * The account can no longer sign in and every session it has ends now (K50). Blocking
	 * its links is a separate call, so the caller decides and audits both.
	 * @return false if it was already suspended
	 * @throws AccountNotFoundException if there is no such account
	 * @throws AdministratorAccountException for administrators
	 */
	@Transactional
	public boolean suspend(UUID userId) {
		AccountSummary account = get(userId).orElseThrow(AccountNotFoundException::new);
		if (account.administrator()) {
			throw new AdministratorAccountException();
		}
		Timestamp now = Timestamp.from(Instant.now(this.clock));
		boolean changed = this.jdbc
			.sql("update users set suspended_at = ?, updated_at = ? where id = ? and suspended_at is null and role <> 'ADMIN'")
			.params(now, now, userId)
			.update() == 1;
		// Also when already suspended: a session that slipped in is ended either way.
		this.sessions.findByPrincipalName(account.email()).keySet().forEach(this.sessions::deleteById);
		return changed;
	}

	/**
	 * Lets the account sign in again. Its links stay blocked until unblocked one by one.
	 * @return false if it was not suspended
	 * @throws AccountNotFoundException if there is no such account
	 */
	@Transactional
	public boolean unsuspend(UUID userId) {
		get(userId).orElseThrow(AccountNotFoundException::new);
		Timestamp now = Timestamp.from(Instant.now(this.clock));
		return this.jdbc.sql("update users set suspended_at = null, updated_at = ? where id = ? and suspended_at is not null")
			.params(now, userId)
			.update() == 1;
	}

	private static AccountSummary summary(ResultSet row, int index) throws SQLException {
		Timestamp verified = row.getTimestamp("email_verified_at");
		Timestamp suspended = row.getTimestamp("suspended_at");
		return new AccountSummary(row.getObject("id", UUID.class), row.getString("email"), row.getString("role"),
				verified != null, (suspended != null) ? suspended.toInstant() : null,
				row.getTimestamp("created_at").toInstant(), row.getObject("workspace_id", UUID.class));
	}

}
