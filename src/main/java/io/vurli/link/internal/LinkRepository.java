package io.vurli.link.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LinkRepository extends JpaRepository<Link, UUID> {

	@Query("""
			select new io.vurli.link.internal.LinkSnapshot(l.targetUrl, l.status, l.expiresAt, l.deletedAt)
			from Link l where l.code = :code""")
	Optional<LinkSnapshot> findSnapshotByCode(String code);

}
