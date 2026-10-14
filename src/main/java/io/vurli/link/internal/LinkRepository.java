package io.vurli.link.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LinkRepository extends JpaRepository<Link, UUID> {

	@Query("""
			select new io.vurli.link.internal.LinkSnapshot(l.targetUrl, l.status, l.expiresAt, l.deletedAt)
			from Link l where l.code = :code""")
	Optional<LinkSnapshot> findSnapshotByCode(String code);

	/** Owner access: always scoped to the workspace, deleted links are invisible. */
	Optional<Link> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

	List<Link> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId, Pageable pageable);

}
