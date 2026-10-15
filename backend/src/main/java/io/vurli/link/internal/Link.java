package io.vurli.link.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import io.vurli.link.LinkBlockedException;
import io.vurli.link.LinkStatus;
import io.vurli.link.LinkView;
import io.vurli.link.ModeratedLink;
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

	/**
	 * @param safetyCheckedAt when the target was found safe, or {@code null} if the check
	 * gave no answer and is still due
	 */
	public static Link create(UUID workspaceId, String code, String targetUrl, Instant expiresAt,
			Instant safetyCheckedAt, Instant now) {
		Link link = new Link();
		link.id = UUID.randomUUID();
		link.workspaceId = workspaceId;
		link.code = code;
		link.targetUrl = targetUrl;
		link.status = LinkStatus.ACTIVE;
		link.expiresAt = stored(expiresAt);
		link.safetyCheckedAt = stored(safetyCheckedAt);
		link.createdAt = stored(now);
		link.updatedAt = link.createdAt;
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

	public String targetUrl() {
		return this.targetUrl;
	}

	/**
	 * A new target carries its own check: whatever was true for the old one does not apply.
	 * @param safetyCheckedAt when the new target was found safe, or {@code null}
	 */
	public void changeTarget(String targetUrl, Instant safetyCheckedAt, Instant now) {
		requireNotBlocked();
		this.targetUrl = targetUrl;
		this.safetyCheckedAt = stored(safetyCheckedAt);
		this.updatedAt = stored(now);
	}

	public void changeExpiry(Instant expiresAt, Instant now) {
		requireNotBlocked();
		this.expiresAt = stored(expiresAt);
		this.updatedAt = stored(now);
	}

	public void disable(Instant now) {
		requireNotBlocked();
		this.status = LinkStatus.DISABLED;
		this.updatedAt = stored(now);
	}

	public void enable(Instant now) {
		requireNotBlocked();
		this.status = LinkStatus.ACTIVE;
		this.updatedAt = stored(now);
	}

	/** Allowed even when blocked: the row stays, so the code is never handed out again. */
	public void delete(Instant now) {
		this.deletedAt = stored(now);
		this.updatedAt = this.deletedAt;
	}

	public void block(Instant now) {
		this.status = LinkStatus.BLOCKED;
		this.updatedAt = stored(now);
	}

	/**
	 * Back to active (K50): unblocking is mostly for mistaken blocks of live links; an owner
	 * who wants it off can turn it off again.
	 */
	public void unblock(Instant now) {
		if (this.status == LinkStatus.BLOCKED) {
			this.status = LinkStatus.ACTIVE;
			this.updatedAt = stored(now);
		}
	}

	public ModeratedLink toModerated() {
		return new ModeratedLink(this.id, this.code, this.targetUrl, this.status, this.workspaceId, this.createdAt,
				this.deletedAt);
	}

	public LinkView toView(LinkProperties properties) {
		return new LinkView(this.id, this.code, properties.shortUrl(this.code), this.targetUrl, this.status,
				this.expiresAt, this.createdAt, this.version);
	}

	/**
	 * PostgreSQL keeps microseconds while {@code Instant.now()} has nanoseconds on Linux.
	 * Truncating here keeps what we return identical to what is stored.
	 */
	private static Instant stored(Instant instant) {
		return (instant != null) ? instant.truncatedTo(ChronoUnit.MICROS) : null;
	}

	private void requireNotBlocked() {
		if (this.status == LinkStatus.BLOCKED) {
			throw new LinkBlockedException();
		}
	}

}
