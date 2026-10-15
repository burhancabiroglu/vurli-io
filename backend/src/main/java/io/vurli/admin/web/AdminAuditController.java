package io.vurli.admin.web;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.vurli.abuse.AuditEntry;
import io.vurli.abuse.AuditLog;
import io.vurli.account.AccountAdministration;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The latest audit log entries, read-only. */
@Controller
class AdminAuditController {

	static final int ENTRIES = 200;

	private final AuditLog audit;

	private final AccountAdministration accounts;

	AdminAuditController(AuditLog audit, AccountAdministration accounts) {
		this.audit = audit;
		this.accounts = accounts;
	}

	@GetMapping("/admin/audit")
	String audit(Model model) {
		List<AuditEntry> entries = this.audit.recent(ENTRIES);
		Set<UUID> actors = entries.stream().map(AuditEntry::actorId).filter(Objects::nonNull).collect(Collectors.toSet());
		model.addAttribute("entries", entries);
		model.addAttribute("actors", this.accounts.emails(actors));
		return "admin/audit";
	}

}
