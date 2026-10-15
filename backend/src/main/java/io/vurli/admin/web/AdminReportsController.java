package io.vurli.admin.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.vurli.abuse.AbuseReport;
import io.vurli.abuse.AbuseReports;
import io.vurli.account.AccountAdministration;
import io.vurli.account.VurliUser;
import io.vurli.audit.AuditLog;
import io.vurli.link.LinkModeration;
import io.vurli.link.ModeratedLink;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Open abuse reports (K51): block the reported link or dismiss the report, with a reason. */
@Controller
@RequestMapping("/admin/reports")
class AdminReportsController {

	static final int RESULTS = 100;

	private final AbuseReports reports;

	private final LinkModeration moderation;

	private final AccountAdministration accounts;

	private final AuditLog audit;

	AdminReportsController(AbuseReports reports, LinkModeration moderation, AccountAdministration accounts,
			AuditLog audit) {
		this.reports = reports;
		this.moderation = moderation;
		this.accounts = accounts;
		this.audit = audit;
	}

	@GetMapping
	String list(Model model) {
		List<AbuseReport> open = this.reports.open(RESULTS);
		Map<UUID, ModeratedLink> links = new HashMap<>();
		open.stream()
			.map(AbuseReport::linkId)
			.filter(Objects::nonNull)
			.distinct()
			.forEach((id) -> links.put(id, this.moderation.get(id)));
		model.addAttribute("reports", open);
		model.addAttribute("links", links);
		model.addAttribute("owners",
				this.accounts.ownerEmails(links.values().stream().map(ModeratedLink::workspaceId).toList()));
		return "admin/reports";
	}

	@PostMapping("/{id}/block")
	String block(@AuthenticationPrincipal VurliUser admin, @PathVariable UUID id, @RequestParam String reason,
			RedirectAttributes flash) {
		String valid = Reasons.valid(reason);
		Optional<AbuseReport> report = this.reports.get(id);
		if (valid == null || report.isEmpty() || report.get().linkId() == null) {
			flash.addFlashAttribute("error", (valid == null) ? "admin.error.reason" : "admin.error.not-found");
			return "redirect:/admin/reports";
		}
		UUID linkId = report.get().linkId();
		this.moderation.block(linkId);
		this.audit.record(admin.userId(), "LINK_BLOCKED", "LINK", linkId.toString(),
				Map.of("code", report.get().code(), "reason", valid, "report", id.toString()));
		resolve(admin, id, "BLOCKED", valid);
		flash.addFlashAttribute("message", "admin.reports.blocked");
		return "redirect:/admin/reports";
	}

	@PostMapping("/{id}/dismiss")
	String dismiss(@AuthenticationPrincipal VurliUser admin, @PathVariable UUID id, @RequestParam String reason,
			RedirectAttributes flash) {
		String valid = Reasons.valid(reason);
		if (valid == null || this.reports.get(id).isEmpty()) {
			flash.addFlashAttribute("error", (valid == null) ? "admin.error.reason" : "admin.error.not-found");
			return "redirect:/admin/reports";
		}
		resolve(admin, id, "DISMISSED", valid);
		flash.addFlashAttribute("message", "admin.reports.dismissed");
		return "redirect:/admin/reports";
	}

	private void resolve(VurliUser admin, UUID reportId, String outcome, String reason) {
		if (this.reports.resolve(reportId, admin.userId())) {
			this.audit.record(admin.userId(), "REPORT_RESOLVED", "REPORT", reportId.toString(),
					Map.of("outcome", outcome, "reason", reason));
		}
	}

}
