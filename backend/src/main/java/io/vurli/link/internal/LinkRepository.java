package io.vurli.link.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LinkRepository extends JpaRepository<Link, UUID> {

	@Query("""
			select new io.vurli.link.internal.LinkSnapshot(l.id, l.targetUrl, l.status, l.expiresAt, l.deletedAt)
			from Link l where l.code = :code""")
	Optional<LinkSnapshot> findSnapshotByCode(String code);

	/** Owner access: always scoped to the workspace, deleted links are invisible. */
	Optional<Link> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

	/** Includes deleted links: the daily quota counts creations, not what is left. */
	long countByWorkspaceIdAndCreatedAtAfter(UUID workspaceId, Instant since);

	/** A slice reads one extra row to know whether a next page exists, without a count query. */
	Slice<Link> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId, Pageable pageable);

}
