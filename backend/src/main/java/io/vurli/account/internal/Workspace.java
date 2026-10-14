package io.vurli.account.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The isolation boundary for links. Every account gets one personal workspace; teams
 * would add members to it later without moving any links.
 */
@Entity
@Table(name = "workspaces")
public class Workspace {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	private UUID ownerId;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected Workspace() {
	}

	public static Workspace personal(UUID ownerId, Instant now) {
		Workspace workspace = new Workspace();
		workspace.id = UUID.randomUUID();
		workspace.name = "Personal";
		workspace.ownerId = ownerId;
		workspace.createdAt = now.truncatedTo(ChronoUnit.MICROS);
		return workspace;
	}

	public UUID id() {
		return this.id;
	}

}
