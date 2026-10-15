package io.vurli.abuse.internal;

import java.time.Clock;
import java.util.Map;

import io.vurli.audit.AuditLog;
import io.vurli.link.LinkModeration;
import io.vurli.link.SafetyCandidate;
import io.vurli.link.TargetSafety;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * Looks up links that were never checked (the service had no answer at creation, or no
 * key was set yet) and rechecks live links weekly, since a target can turn dangerous
 * later (K24). A listed target is blocked and audited as a system action.
 * <p>
 * Several missing answers in a row mean an outage: the run stops instead of burning the
 * quota. A single one is skipped, so one URL the service keeps failing on cannot hold up
 * every link behind it.
 */
public class SafetyScanner {

	static final int OUTAGE_AFTER = 3;

	private static final Logger log = LoggerFactory.getLogger(SafetyScanner.class);

	private final LinkModeration moderation;

	private final TargetSafety safety;

	private final AuditLog audit;

	private final WebRiskProperties properties;

	private final Clock clock;

	public SafetyScanner(LinkModeration moderation, TargetSafety safety, AuditLog audit, WebRiskProperties properties,
			Clock clock) {
		this.moderation = moderation;
		this.safety = safety;
		this.audit = audit;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${vurli.web-risk.scan-interval:5m}", initialDelayString = "1m")
	public void scheduled() {
		if (this.properties.enabled()) {
			scan();
		}
	}

	/** @return how many links were blocked */
	public int scan() {
		int blocked = 0;
		int unanswered = 0;
		for (SafetyCandidate link : this.moderation.dueForSafetyCheck(
				this.clock.instant().minus(this.properties.recheckAfter()), this.properties.scanBatch())) {
			TargetSafety.Verdict verdict = this.safety.check(link.targetUrl());
			if (verdict == TargetSafety.Verdict.UNKNOWN) {
				if (++unanswered == OUTAGE_AFTER) {
					break;
				}
				continue;
			}
			unanswered = 0;
			if (verdict == TargetSafety.Verdict.SAFE) {
				this.moderation.recordSafe(link.id(), link.targetUrl());
			}
			else if (this.moderation.blockIfTarget(link.id(), link.targetUrl())) {
				blocked++;
				this.audit.record(null, "LINK_BLOCKED_BY_SCAN", "LINK", link.id().toString(),
						Map.of("code", link.code(), "reason", "Web Risk"));
			}
		}
		if (blocked > 0) {
			log.warn("Safety scan blocked {} links", blocked);
		}
		return blocked;
	}

}
