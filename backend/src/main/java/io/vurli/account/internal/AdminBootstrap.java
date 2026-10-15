package io.vurli.account.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.vurli.abuse.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator changes that only someone with access to the server's configuration can
 * make: promoting an account (K48) and removing a lost second factor (K49). Both run at
 * startup, are written to the audit log as system actions and do nothing when already
 * done, so a value left in the configuration is harmless but logged on every start.
 */
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final AdminProperties properties;

	private final JdbcClient jdbc;

	private final AuditLog audit;

	private final Clock clock;

	public AdminBootstrap(AdminProperties properties, JdbcClient jdbc, AuditLog audit, Clock clock) {
		this.properties = properties;
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!this.properties.promote().isBlank()) {
			promote(EmailAddress.normalize(this.properties.promote()));
		}
		if (!this.properties.resetTotp().isBlank()) {
			resetTotp(EmailAddress.normalize(this.properties.resetTotp()));
		}
	}

	/** Only a verified account: the address must be proven to belong to the person. */
	void promote(String email) {
		Optional<UUID> user = this.jdbc
			.sql("select id from users where email = ? and email_verified_at is not null")
			.param(email)
			.query(UUID.class)
			.optional();
		if (user.isEmpty()) {
			log.warn("vurli.admin.promote: no verified account for the configured address; nothing changed");
			return;
		}
		int changed = this.jdbc.sql("update users set role = 'ADMIN', updated_at = ? where id = ? and role <> 'ADMIN'")
			.params(Timestamp.from(this.clock.instant()), user.get())
			.update();
		if (changed == 1) {
			this.audit.record(null, "ADMIN_PROMOTED", "USER", user.get().toString(), Map.of());
			log.warn("Account {} is now an administrator (vurli.admin.promote)", user.get());
		}
		else {
			log.info("vurli.admin.promote: account {} is already an administrator; remove the setting", user.get());
		}
	}

	void resetTotp(String email) {
		Optional<UUID> user = this.jdbc.sql("select id from users where email = ? and role = 'ADMIN'")
			.param(email)
			.query(UUID.class)
			.optional();
		if (user.isEmpty()) {
			log.warn("vurli.admin.reset-totp: no administrator with the configured address; nothing changed");
			return;
		}
		int removed = this.jdbc.sql("delete from admin_credentials where user_id = ?").param(user.get()).update();
		this.jdbc.sql("delete from admin_backup_codes where user_id = ?").param(user.get()).update();
		if (removed == 1) {
			this.audit.record(null, "ADMIN_TOTP_RESET", "USER", user.get().toString(), Map.of());
			log.warn("Second factor of administrator {} removed (vurli.admin.reset-totp); remove the setting now",
					user.get());
		}
	}

}
