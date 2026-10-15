package io.vurli.admin.web;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.vurli.account.AccountAdministration;
import io.vurli.account.VurliUser;
import io.vurli.audit.AuditLog;
import io.vurli.link.LinkModeration;
import io.vurli.link.LinkNotFoundException;
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
import org.springframework.web.util.UriComponentsBuilder;

/** Link search and blocking. Every change is in the audit log with its reason. */
@Controller
@RequestMapping("/admin/links")
class AdminLinksController {

	static final int RESULTS = 50;

	private final LinkModeration moderation;

	private final AccountAdministration accounts;

	private final AuditLog audit;

	AdminLinksController(LinkModeration moderation, AccountAdministration accounts, AuditLog audit) {
		this.moderation = moderation;
		this.accounts = accounts;
		this.audit = audit;
	}

	/** By exact code or by a part of the target. */
	@GetMapping
	String search(@RequestParam(defaultValue = "") String q, Model model) {
		List<ModeratedLink> links = this.moderation.search(q, RESULTS);
		Set<UUID> workspaces = links.stream().map(ModeratedLink::workspaceId).collect(Collectors.toSet());
		model.addAttribute("q", q);
		model.addAttribute("links", links);
		model.addAttribute("owners", this.accounts.ownerEmails(workspaces));
		return "admin/links";
	}

	@PostMapping("/{id}/block")
	String block(@AuthenticationPrincipal VurliUser admin, @PathVariable UUID id, @RequestParam String reason,
			@RequestParam(defaultValue = "") String q, RedirectAttributes flash) {
		return change(admin, id, reason, q, flash, "LINK_BLOCKED", "admin.links.blocked", this.moderation::block);
	}

	@PostMapping("/{id}/unblock")
	String unblock(@AuthenticationPrincipal VurliUser admin, @PathVariable UUID id, @RequestParam String reason,
			@RequestParam(defaultValue = "") String q, RedirectAttributes flash) {
		return change(admin, id, reason, q, flash, "LINK_UNBLOCKED", "admin.links.unblocked", this.moderation::unblock);
	}

	private String change(VurliUser admin, UUID id, String reason, String q, RedirectAttributes flash, String action,
			String doneMessage, Consumer<UUID> change) {
		String back = UriComponentsBuilder.fromPath("/admin/links").queryParam("q", q).encode().build().toUriString();
		String valid = Reasons.valid(reason);
		if (valid == null) {
			flash.addFlashAttribute("error", "admin.error.reason");
			return "redirect:" + back;
		}
		ModeratedLink link;
		try {
			link = this.moderation.get(id);
			change.accept(id);
		}
		catch (LinkNotFoundException ex) {
			flash.addFlashAttribute("error", "admin.error.not-found");
			return "redirect:" + back;
		}
		this.audit.record(admin.userId(), action, "LINK", id.toString(),
				Map.of("code", link.code(), "reason", valid));
		flash.addFlashAttribute("message", doneMessage);
		return "redirect:" + back;
	}

}
