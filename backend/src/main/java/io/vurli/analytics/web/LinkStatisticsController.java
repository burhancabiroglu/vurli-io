package io.vurli.analytics.web;

import java.util.UUID;

import io.vurli.account.VurliUser;
import io.vurli.analytics.ClickStatistics;
import io.vurli.analytics.LinkStatistics;
import io.vurli.link.LinkManagement;
import io.vurli.link.LinkNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
class LinkStatisticsController {

	private final LinkManagement links;

	private final ClickStatistics statistics;

	LinkStatisticsController(LinkManagement links, ClickStatistics statistics) {
		this.links = links;
		this.statistics = statistics;
	}

	/** Ownership first: another workspace's link is "not found", like everywhere else. */
	@GetMapping("/{id}/stats")
	LinkStatistics stats(@AuthenticationPrincipal VurliUser user, @PathVariable UUID id) {
		this.links.get(user.workspaceId(), id);
		return this.statistics.forLink(id);
	}

	@ExceptionHandler(LinkNotFoundException.class)
	ProblemDetail notFound() {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problem.setProperty("code", "link_not_found");
		return problem;
	}

}
