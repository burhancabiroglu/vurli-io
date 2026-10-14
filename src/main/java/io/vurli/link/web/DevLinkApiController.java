package io.vurli.link.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.vurli.link.AliasTakenException;
import io.vurli.link.CreateLinkRequest;
import io.vurli.link.InvalidAliasException;
import io.vurli.link.InvalidExpiryException;
import io.vurli.link.InvalidTargetUrlException;
import io.vurli.link.LinkBlockedException;
import io.vurli.link.LinkManagement;
import io.vurli.link.LinkNotFoundException;
import io.vurli.link.LinkVersionConflictException;
import io.vurli.link.LinkView;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Development-only JSON API for trying links with curl before accounts exist. Every
 * request acts for one fixed workspace. Replaced by the authenticated panel in M2.
 * <p>
 * Changes carry the version the caller last saw; a mismatch is a 409, not an overwrite.
 */
@Profile("dev")
@RestController
@RequestMapping("/dev/api/links")
class DevLinkApiController {

	private final LinkManagement links;

	private final UUID workspaceId;

	DevLinkApiController(LinkManagement links, @Value("${vurli.dev.workspace-id}") UUID workspaceId) {
		this.links = links;
		this.workspaceId = workspaceId;
	}

	@PostMapping
	ResponseEntity<LinkView> create(@RequestBody CreateLinkRequest request) {
		LinkView link = this.links.create(this.workspaceId, request);
		return ResponseEntity.created(URI.create("/dev/api/links/" + link.id())).body(link);
	}

	@GetMapping
	List<LinkView> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return this.links.list(this.workspaceId, page, size);
	}

	@GetMapping("/{id}")
	LinkView get(@PathVariable UUID id) {
		return this.links.get(this.workspaceId, id);
	}

	@PutMapping("/{id}/target")
	LinkView changeTarget(@PathVariable UUID id, @RequestBody ChangeTarget request) {
		return this.links.changeTarget(this.workspaceId, id, request.version(), request.targetUrl());
	}

	@PutMapping("/{id}/expiry")
	LinkView changeExpiry(@PathVariable UUID id, @RequestBody ChangeExpiry request) {
		return this.links.changeExpiry(this.workspaceId, id, request.version(), request.expiresAt());
	}

	@PostMapping("/{id}/disable")
	LinkView disable(@PathVariable UUID id, @RequestBody Versioned request) {
		return this.links.disable(this.workspaceId, id, request.version());
	}

	@PostMapping("/{id}/enable")
	LinkView enable(@PathVariable UUID id, @RequestBody Versioned request) {
		return this.links.enable(this.workspaceId, id, request.version());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam long version) {
		this.links.delete(this.workspaceId, id, version);
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(InvalidTargetUrlException.class)
	ProblemDetail invalidTarget(InvalidTargetUrlException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_target", ex.reason().name());
	}

	@ExceptionHandler(InvalidAliasException.class)
	ProblemDetail invalidAlias(InvalidAliasException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_alias", ex.reason().name());
	}

	@ExceptionHandler(InvalidExpiryException.class)
	ProblemDetail invalidExpiry() {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_expiry", null);
	}

	@ExceptionHandler(AliasTakenException.class)
	ProblemDetail aliasTaken() {
		return problem(HttpStatus.CONFLICT, "alias_taken", null);
	}

	@ExceptionHandler(LinkVersionConflictException.class)
	ProblemDetail versionConflict() {
		return problem(HttpStatus.CONFLICT, "version_conflict", null);
	}

	@ExceptionHandler(LinkBlockedException.class)
	ProblemDetail blocked() {
		return problem(HttpStatus.CONFLICT, "link_blocked", null);
	}

	@ExceptionHandler(LinkNotFoundException.class)
	ProblemDetail notFound() {
		return problem(HttpStatus.NOT_FOUND, "link_not_found", null);
	}

	private static ProblemDetail problem(HttpStatus status, String code, String reason) {
		ProblemDetail problem = ProblemDetail.forStatus(status);
		problem.setProperty("code", code);
		if (reason != null) {
			problem.setProperty("reason", reason);
		}
		return problem;
	}

	record ChangeTarget(long version, String targetUrl) {
	}

	record ChangeExpiry(long version, Instant expiresAt) {
	}

	record Versioned(long version) {
	}

}
