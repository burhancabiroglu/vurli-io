package io.vurli.admin.web;

import java.util.Map;
import java.util.UUID;

import io.vurli.account.AccountAdministration;
import io.vurli.account.AccountNotFoundException;
import io.vurli.account.AccountSummary;
import io.vurli.account.AdministratorAccountException;
import io.vurli.account.VurliUser;
import io.vurli.audit.AuditLog;
import io.vurli.link.LinkModeration;

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

/**
 * Account search, suspension and blocking all links of an account (K50). Every change is
 * in the audit log with its reason.
 */
@Controller
@RequestMapping("/admin/accounts")
class AdminAccountsController {

	static final int RESULTS = 50;

	private final AccountAdministration accounts;

	private final LinkModeration moderation;

	private final AuditLog audit;

	AdminAccountsController(AccountAdministration accounts, LinkModeration moderation, AuditLog audit) {
		this.accounts = accounts;
		this.moderation = moderation;
		this.audit = audit;
	}

	@GetMapping
	String search(@RequestParam(defaultValue = "") String q, Model model) {
		model.addAttribute("q", q);
		model.addAttribute("accounts", this.accounts.search(q, RESULTS));
		return "admin/accounts";
	}

	/** One action: no more sign-in, sessions ended, every link blocked. */
	@PostMapping("/{id}/suspend")
	String suspend(@AuthenticationPrincipal VurliUser admin, @PathVariable UUID id, @RequestParam String reason,
			@RequestParam(defaultValue = "") String q, RedirectAttributes flash) {
		String back = back(q);
		String valid = Reasons.valid(reason);
		if (valid == null) {
			return fail(flash, back, "admin.error.reason");
		}
		AccountSummary account;
		try {
			account = this.accounts.get(id).orElseThrow(AccountNotFoundException::new);
			this.accounts.suspend(id);
		}
		catch (AccountNotFoundException ex) {
			return fail(flash, back, "admin.error.not-found");
		}
		catch (AdministratorAccountException ex) {
			return fail(flash, back, "admin.error.administrator");
		}
		int blocked = (account.workspaceId() != null) ? this.moderation.blockAll(account.workspaceId()) : 0;
		this.audit.record(admin.userId(), "ACCOUNT_SUSPENDED", "USER", id.toString(),
				Map.of("reason", valid, "linksBlocked", blocked));
		flash.addFlashAttribute("message", "admin.accounts.suspended");
		return "redirect:" + back;
	}

	@PostMapping("/{id}/unsuspend")
	String unsuspend(@AuthenticationPrincipal VurliUser admin, @PathVariable UUID id, @RequestParam String reason,
			@RequestParam(defaultValue = "") String q, RedirectAttributes flash) {
		String back = back(q);
		String valid = Reasons.valid(reason);
		if (valid == null) {
			return fail(flash, back, "admin.error.reason");
		}
		try {
			this.accounts.unsuspend(id);
		}
		catch (AccountNotFoundException ex) {
			return fail(flash, back, "admin.error.not-found");
		}
		this.audit.record(admin.userId(), "ACCOUNT_UNSUSPENDED", "USER", id.toString(), Map.of("reason", valid));
		flash.addFlashAttribute("message", "admin.accounts.unsuspended");
		return "redirect:" + back;
	}

	/** Blocks every link of the account but lets it keep signing in. */
	@PostMapping("/{id}/block-links")
	String blockLinks(@AuthenticationPrincipal VurliUser admin, @PathVariable UUID id, @RequestParam String reason,
			@RequestParam(defaultValue = "") String q, RedirectAttributes flash) {
		String back = back(q);
		String valid = Reasons.valid(reason);
		if (valid == null) {
			return fail(flash, back, "admin.error.reason");
		}
		AccountSummary account = this.accounts.get(id).orElse(null);
		if (account == null || account.workspaceId() == null) {
			return fail(flash, back, "admin.error.not-found");
		}
		int blocked = this.moderation.blockAll(account.workspaceId());
		this.audit.record(admin.userId(), "ACCOUNT_LINKS_BLOCKED", "USER", id.toString(),
				Map.of("reason", valid, "linksBlocked", blocked));
		flash.addFlashAttribute("message", "admin.accounts.links-blocked");
		return "redirect:" + back;
	}

	private static String back(String q) {
		return UriComponentsBuilder.fromPath("/admin/accounts").queryParam("q", q).encode().build().toUriString();
	}

	private static String fail(RedirectAttributes flash, String back, String messageKey) {
		flash.addFlashAttribute("error", messageKey);
		return "redirect:" + back;
	}

}
