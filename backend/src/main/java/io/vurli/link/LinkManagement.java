package io.vurli.link;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import io.vurli.link.internal.AliasPolicy;
import io.vurli.link.internal.CodeGenerator;
import io.vurli.link.internal.Link;
import io.vurli.link.internal.LinkCache;
import io.vurli.link.internal.LinkProperties;
import io.vurli.link.internal.LinkRepository;
import io.vurli.link.internal.TargetUrlValidator;
import org.hibernate.exception.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates and manages links within a workspace.
 */
@Service
public class LinkManagement {

	static final int MAX_CODE_ATTEMPTS = 3;

	static final int MAX_PAGE_SIZE = 100;

	private static final String CODE_CONSTRAINT = "uk_links_code";

	private final LinkRepository links;

	private final TransactionTemplate transaction;

	private final TargetUrlValidator targetUrlValidator;

	private final CodeGenerator codeGenerator;

	private final LinkCache cache;

	private final LinkProperties properties;

	private final Clock clock;

	public LinkManagement(LinkRepository links, PlatformTransactionManager transactionManager,
			TargetUrlValidator targetUrlValidator, CodeGenerator codeGenerator, LinkCache cache,
			LinkProperties properties, Clock clock) {
		this.links = links;
		this.transaction = new TransactionTemplate(transactionManager);
		this.targetUrlValidator = targetUrlValidator;
		this.codeGenerator = codeGenerator;
		this.cache = cache;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Creates a link with the requested alias, or with a random code when no alias is
	 * given.
	 * @throws InvalidTargetUrlException if the target is not an acceptable destination
	 * @throws InvalidAliasException if the alias breaks the alias rules
	 * @throws AliasTakenException if the alias is already used, even by a deleted link
	 * @throws InvalidExpiryException if the expiry is not in the future
	 */
	public LinkView create(UUID workspaceId, CreateLinkRequest request) {
		String targetUrl = this.targetUrlValidator.validate(request.targetUrl());
		Instant now = this.clock.instant();
		if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
			throw new InvalidExpiryException();
		}

		if (request.alias() != null && !request.alias().isBlank()) {
			String alias = AliasPolicy.normalize(request.alias());
			return insert(workspaceId, alias, targetUrl, request.expiresAt(), now)
				.orElseThrow(() -> new AliasTakenException(alias));
		}

		for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
			String code = this.codeGenerator.next();
			if (AliasPolicy.isReserved(code)) {
				continue;
			}
			Optional<LinkView> created = insert(workspaceId, code, targetUrl, request.expiresAt(), now);
			if (created.isPresent()) {
				return created.get();
			}
		}
		throw new IllegalStateException("No free code found after " + MAX_CODE_ATTEMPTS + " attempts");
	}

	/**
	 * @throws LinkNotFoundException if the link is not in this workspace or was deleted
	 */
	public LinkView get(UUID workspaceId, UUID linkId) {
		return this.links.findByIdAndWorkspaceIdAndDeletedAtIsNull(linkId, workspaceId)
			.map(this::view)
			.orElseThrow(LinkNotFoundException::new);
	}

	/**
	 * Newest first. The page size is capped so a single request cannot load a whole
	 * workspace.
	 */
	public LinkPage list(UUID workspaceId, int page, int size) {
		PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		Slice<Link> slice = this.links.findByWorkspaceIdAndDeletedAtIsNull(workspaceId, request);
		return new LinkPage(slice.map(this::view).getContent(), request.getPageNumber(), request.getPageSize(),
				slice.hasNext());
	}

	/**
	 * The new target goes through the same validation as on creation and loses any
	 * previous safety check.
	 * @throws InvalidTargetUrlException if the target is not an acceptable destination
	 */
	public LinkView changeTarget(UUID workspaceId, UUID linkId, long expectedVersion, String targetUrl) {
		String validated = this.targetUrlValidator.validate(targetUrl);
		return mutate(workspaceId, linkId, expectedVersion, (link, now) -> link.changeTarget(validated, now));
	}

	/**
	 * @param expiresAt the new expiry, or {@code null} to remove it
	 * @throws InvalidExpiryException if the expiry is not in the future
	 */
	public LinkView changeExpiry(UUID workspaceId, UUID linkId, long expectedVersion, Instant expiresAt) {
		return mutate(workspaceId, linkId, expectedVersion, (link, now) -> {
			if (expiresAt != null && !expiresAt.isAfter(now)) {
				throw new InvalidExpiryException();
			}
			link.changeExpiry(expiresAt, now);
		});
	}

	public LinkView disable(UUID workspaceId, UUID linkId, long expectedVersion) {
		return mutate(workspaceId, linkId, expectedVersion, Link::disable);
	}

	public LinkView enable(UUID workspaceId, UUID linkId, long expectedVersion) {
		return mutate(workspaceId, linkId, expectedVersion, Link::enable);
	}

	/**
	 * The link stops redirecting and disappears for its owner, but its code stays
	 * reserved forever.
	 */
	public void delete(UUID workspaceId, UUID linkId, long expectedVersion) {
		mutate(workspaceId, linkId, expectedVersion, Link::delete);
	}

	/**
	 * Applies an owner change. The version is checked twice: against the loaded row here,
	 * and by JPA's {@code @Version} on flush for a change committed in between.
	 * @throws LinkNotFoundException if the link is not in this workspace or was deleted
	 * @throws LinkVersionConflictException if the link changed since the caller read it
	 * @throws LinkBlockedException if an administrator blocked the link
	 */
	private LinkView mutate(UUID workspaceId, UUID linkId, long expectedVersion, BiConsumer<Link, Instant> change) {
		LinkView changed;
		try {
			changed = this.transaction.execute((status) -> {
				Link link = this.links.findByIdAndWorkspaceIdAndDeletedAtIsNull(linkId, workspaceId)
					.orElseThrow(LinkNotFoundException::new);
				if (!link.hasVersion(expectedVersion)) {
					throw new LinkVersionConflictException();
				}
				change.accept(link, this.clock.instant());
				return view(this.links.saveAndFlush(link));
			});
		}
		catch (OptimisticLockingFailureException ex) {
			throw new LinkVersionConflictException();
		}
		this.cache.invalidate(changed.code());
		return changed;
	}

	/**
	 * Inserts in its own transaction and relies on the unique constraint, not on a prior
	 * lookup, so concurrent requests for the same code cannot both succeed. A failed
	 * insert aborts the transaction, which is why each attempt gets a fresh one.
	 * @return the created link, or empty if the code is already taken
	 */
	private Optional<LinkView> insert(UUID workspaceId, String code, String targetUrl, Instant expiresAt,
			Instant now) {
		try {
			LinkView created = this.transaction.execute((status) -> view(
					this.links.saveAndFlush(Link.create(workspaceId, code, targetUrl, expiresAt, now))));
			// After commit: drops a cached "not found" for this code.
			this.cache.invalidate(code);
			return Optional.ofNullable(created);
		}
		catch (DataIntegrityViolationException ex) {
			if (isCodeConflict(ex)) {
				return Optional.empty();
			}
			throw ex;
		}
	}

	private LinkView view(Link link) {
		return link.toView(this.properties);
	}

	private static boolean isCodeConflict(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return CODE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
			}
		}
		return false;
	}

}
