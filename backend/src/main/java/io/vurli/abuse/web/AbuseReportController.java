package io.vurli.abuse.web;

import java.util.Locale;

import io.vurli.abuse.AbuseReports;
import io.vurli.abuse.InvalidReportException;
import io.vurli.abuse.ReportReason;
import io.vurli.ratelimit.ClientAddresses;
import io.vurli.ratelimit.RateLimit;
import io.vurli.ratelimit.RateLimitExceededException;
import io.vurli.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The public report form on vurli.io/abuse (K51). The answer is the same whether the
 * reported code exists or not.
 */
@Controller
class AbuseReportController {

	private final AbuseReports reports;

	private final RateLimiter rateLimiter;

	private final ClientAddresses clientAddresses;

	AbuseReportController(AbuseReports reports, RateLimiter rateLimiter, ClientAddresses clientAddresses) {
		this.reports = reports;
		this.rateLimiter = rateLimiter;
		this.clientAddresses = clientAddresses;
	}

	@GetMapping("/abuse")
	String form(@RequestParam(defaultValue = "") String link, Model model, HttpServletResponse response) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		model.addAttribute("link", link);
		model.addAttribute("reasons", ReportReason.values());
		return "abuse/report";
	}

	@PostMapping("/abuse")
	String submit(@RequestParam(defaultValue = "") String link, @RequestParam(defaultValue = "OTHER") ReportReason reason,
			@RequestParam(defaultValue = "") String details, @RequestParam(defaultValue = "") String email,
			HttpServletRequest request, HttpServletResponse response, Model model) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		try {
			this.rateLimiter.consume(RateLimit.ABUSE_REPORTS_PER_IP, this.clientAddresses.rateLimitKey(request));
			this.reports.submit(link, reason, details, email);
			return "abuse/thanks";
		}
		catch (RateLimitExceededException ex) {
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			return form(model, link, reason, details, email, "abuse.error.too-many");
		}
		catch (InvalidReportException ex) {
			response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
			return form(model, link, reason, details, email, "abuse.error." + ex.field().name().toLowerCase(Locale.ROOT));
		}
	}

	/** The form again with what was typed, so nothing has to be entered twice. */
	private static String form(Model model, String link, ReportReason reason, String details, String email,
			String error) {
		model.addAttribute("link", link);
		model.addAttribute("reason", reason);
		model.addAttribute("details", details);
		model.addAttribute("email", email);
		model.addAttribute("reasons", ReportReason.values());
		model.addAttribute("error", error);
		return "abuse/report";
	}

}
