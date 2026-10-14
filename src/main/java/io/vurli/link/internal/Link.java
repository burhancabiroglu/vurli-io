package io.vurli.link.internal;

import java.time.Instant;
import java.util.UUID;

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

	public LinkView toView() {
		return new LinkView(this.id, this.code, this.targetUrl, this.status, this.expiresAt, this.createdAt,
				this.version);
	}

}
