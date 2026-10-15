package io.vurli.link.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	/**
	 * Administrator search: the exact code, or a part of the target. {@code pattern} is a
	 * LIKE pattern whose wildcards in the user's text are already escaped with '\'.
	 */
	@Query("""
			select l from Link l
			where l.code = :code or lower(l.targetUrl) like :pattern escape '\\'
			order by l.createdAt desc, l.id desc""")
	List<Link> search(String code, String pattern, Pageable pageable);

	@Query("select l.code from Link l where l.workspaceId = :workspaceId and l.status <> io.vurli.link.LinkStatus.BLOCKED")
	List<String> findUnblockedCodes(UUID workspaceId);

	/**
	 * Blocks every link of a workspace in one statement. The version goes up like with any
	 * change, so an owner change that read the old row fails instead of undoing the block.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Link l set l.status = io.vurli.link.LinkStatus.BLOCKED, l.version = l.version + 1, l.updatedAt = :now
			where l.workspaceId = :workspaceId and l.status <> io.vurli.link.LinkStatus.BLOCKED""")
	int blockAll(UUID workspaceId, Instant now);

	/** A slice reads one extra row to know whether a next page exists, without a count query. */
	Slice<Link> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId, Pageable pageable);

}
