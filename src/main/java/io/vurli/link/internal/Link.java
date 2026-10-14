package io.vurli.link.internal;

import java.time.Instant;
import java.util.UUID;

import io.vurli.link.LinkBlockedException;
import io.vurli.link.LinkStatus;
import io.vurli.link.LinkView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "links")
public class Link {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID workspaceId;

	@Column(nullable = false, updatable = false)
	private String code;

	@Column(nullable = false)
	private String targetUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LinkStatus status;

	private Instant expiresAt;

	private Instant deletedAt;

	private Instant safetyCheckedAt;

	@Version
	private Long version;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Link() {
	}

	public static Link create(UUID workspaceId, String code, String targetUrl, Instant expiresAt, Instant now) {
		Link link = new Link();
		link.id = UUID.randomUUID();
		link.workspaceId = workspaceId;
		link.code = code;
		link.targetUrl = targetUrl;
		link.status = LinkStatus.ACTIVE;
		link.expiresAt = expiresAt;
		link.createdAt = now;
		link.updatedAt = now;
		return link;
	}

	/**
	 * Not named {@code version()}: Spring Data reads the {@code version} property to tell
	 * new entities apart, and would pick up a primitive accessor that fails on null.
	 */
	public boolean hasVersion(long expectedVersion) {
		return this.version != null && this.version == expectedVersion;
	}

	public String code() {
		return this.code;
	}

	/** A new target has not been safety checked yet, whatever was true for the old one. */
	public void changeTarget(String targetUrl, Instant now) {
		requireNotBlocked();
		this.targetUrl = targetUrl;
		this.safetyCheckedAt = null;
		this.updatedAt = now;
	}

	public void changeExpiry(Instant expiresAt, Instant now) {
		requireNotBlocked();
		this.expiresAt = expiresAt;
		this.updatedAt = now;
	}

	public void disable(Instant now) {
		requireNotBlocked();
		this.status = LinkStatus.DISABLED;
		this.updatedAt = now;
	}

	public void enable(Instant now) {
		requireNotBlocked();
		this.status = LinkStatus.ACTIVE;
		this.updatedAt = now;
	}

	/** Allowed even when blocked: the row stays, so the code is never handed out again. */
	public void delete(Instant now) {
		this.deletedAt = now;
		this.updatedAt = now;
	}

	public void block(Instant now) {
		this.status = LinkStatus.BLOCKED;
		this.updatedAt = now;
	}

	public LinkView toView() {
		return new LinkView(this.id, this.code, this.targetUrl, this.status, this.expiresAt, this.createdAt,
				this.version);
	}

	private void requireNotBlocked() {
		if (this.status == LinkStatus.BLOCKED) {
			throw new LinkBlockedException();
		}
	}

}
