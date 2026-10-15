package io.vurli.link.web;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import io.vurli.abuse.RateLimit;
import io.vurli.abuse.RateLimiter;
import io.vurli.account.AccountManagement;
import io.vurli.account.VurliUser;
import io.vurli.link.CreateLinkRequest;
import io.vurli.link.LinkManagement;
import io.vurli.link.LinkPage;
import io.vurli.link.LinkQrCodes;
import io.vurli.link.LinkView;
import io.vurli.link.QrCode;
import io.vurli.link.QrFormat;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Link management for the signed-in user. The workspace always comes from the session,
 * never from the request, so ids of other workspaces simply are not found.
 * <p>
 * Changes carry the version the caller last saw; a mismatch is a 409, not an overwrite.
 */
@RestController
@RequestMapping("/api/v1/links")
class LinkApiController {

	private final LinkManagement links;

	private final LinkQrCodes qrCodes;

	private final AccountManagement accounts;

	private final RateLimiter rateLimiter;

	LinkApiController(LinkManagement links, LinkQrCodes qrCodes, AccountManagement accounts,
			RateLimiter rateLimiter) {
		this.links = links;
		this.qrCodes = qrCodes;
		this.accounts = accounts;
		this.rateLimiter = rateLimiter;
	}

	/** Unverified accounts can sign in but not create links (K34). */
	@PostMapping
	ResponseEntity<LinkView> create(@AuthenticationPrincipal VurliUser user, @RequestBody CreateLinkRequest request) {
		if (!this.accounts.isEmailVerified(user.userId())) {
			throw new EmailNotVerifiedException();
		}
		this.rateLimiter.consume(RateLimit.LINK_CREATIONS_PER_USER, user.userId().toString());
		LinkView link = this.links.create(user.workspaceId(), request);
		return ResponseEntity.created(URI.create("/api/v1/links/" + link.id())).body(link);
	}

	@GetMapping
	LinkPage list(@AuthenticationPrincipal VurliUser user, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return this.links.list(user.workspaceId(), page, size);
	}

	@GetMapping("/{id}")
	LinkView get(@AuthenticationPrincipal VurliUser user, @PathVariable UUID id) {
		return this.links.get(user.workspaceId(), id);
	}

	/** A download of the QR code for the short link; {@code format} is {@code png} or {@code svg}. */
	@GetMapping("/{id}/qr")
	ResponseEntity<byte[]> qr(@AuthenticationPrincipal VurliUser user, @PathVariable UUID id,
			@RequestParam(defaultValue = "png") String format) {
		QrFormat qrFormat;
		try {
			qrFormat = QrFormat.fromExtension(format);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidQrFormatException();
		}
		QrCode qr = this.qrCodes.render(user.workspaceId(), id, qrFormat);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(qr.format().mediaType()))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(qr.fileName()).build().toString())
			.cacheControl(CacheControl.noStore())
			.body(qr.content());
	}

	@PutMapping("/{id}/target")
	LinkView changeTarget(@AuthenticationPrincipal VurliUser user, @PathVariable UUID id,
			@RequestBody ChangeTarget request) {
		return this.links.changeTarget(user.workspaceId(), id, request.version(), request.targetUrl());
	}

	@PutMapping("/{id}/expiry")
	LinkView changeExpiry(@AuthenticationPrincipal VurliUser user, @PathVariable UUID id,
			@RequestBody ChangeExpiry request) {
		return this.links.changeExpiry(user.workspaceId(), id, request.version(), request.expiresAt());
	}

	@PostMapping("/{id}/disable")
	LinkView disable(@AuthenticationPrincipal VurliUser user, @PathVariable UUID id, @RequestBody Versioned request) {
		return this.links.disable(user.workspaceId(), id, request.version());
	}

	@PostMapping("/{id}/enable")
	LinkView enable(@AuthenticationPrincipal VurliUser user, @PathVariable UUID id, @RequestBody Versioned request) {
		return this.links.enable(user.workspaceId(), id, request.version());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@AuthenticationPrincipal VurliUser user, @PathVariable UUID id,
			@RequestParam long version) {
		this.links.delete(user.workspaceId(), id, version);
		return ResponseEntity.noContent().build();
	}

	record ChangeTarget(long version, String targetUrl) {
	}

	record ChangeExpiry(long version, Instant expiresAt) {
	}

	record Versioned(long version) {
	}

	static class InvalidQrFormatException extends RuntimeException {

		InvalidQrFormatException() {
			super("Unsupported QR format");
		}

	}

	static class EmailNotVerifiedException extends RuntimeException {

		EmailNotVerifiedException() {
			super("Email address not verified");
		}

	}

}
