package io.vurli.admin.web;

import io.vurli.account.TwoFactorAuthentication;
import io.vurli.account.VurliUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The admin panel's start page; search and blocking arrive in M3 slice 3b. */
@Controller
class AdminHomeController {

	private final TwoFactorAuthentication twoFactor;

	AdminHomeController(TwoFactorAuthentication twoFactor) {
		this.twoFactor = twoFactor;
	}

	@GetMapping("/admin")
	String home(@AuthenticationPrincipal VurliUser user, Model model) {
		model.addAttribute("email", user.getUsername());
		model.addAttribute("backupCodes", this.twoFactor.remainingBackupCodes(user.userId()));
		return "admin/home";
	}

}
