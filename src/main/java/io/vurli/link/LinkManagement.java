package io.vurli.link;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import io.vurli.link.internal.AliasPolicy;
import io.vurli.link.internal.CodeGenerator;
import io.vurli.link.internal.Link;
import io.vurli.link.internal.LinkCache;
import io.vurli.link.internal.LinkRepository;
import io.vurli.link.internal.TargetUrlValidator;
import org.hibernate.exception.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates and manages links within a workspace.
 */
@Service
public class LinkManagement {

	static final int MAX_CODE_ATTEMPTS = 3;

	private static final String CODE_CONSTRAINT = "uk_links_code";

	private final LinkRepository links;

	private final TransactionTemplate transaction;

	private final TargetUrlValidator targetUrlValidator;

	private final CodeGenerator codeGenerator;

	private final LinkCache cache;

	private final Clock clock;

	public LinkManagement(LinkRepository links, PlatformTransactionManager transactionManager,
			TargetUrlValidator targetUrlValidator, CodeGenerator codeGenerator, LinkCache cache, Clock clock) {
		this.links = links;
		this.transaction = new TransactionTemplate(transactionManager);
		this.targetUrlValidator = targetUrlValidator;
		this.codeGenerator = codeGenerator;
		this.cache = cache;
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
	 * Inserts in its own transaction and relies on the unique constraint, not on a prior
	 * lookup, so concurrent requests for the same code cannot both succeed. A failed
	 * insert aborts the transaction, which is why each attempt gets a fresh one.
	 * @return the created link, or empty if the code is already taken
	 */
	private Optional<LinkView> insert(UUID workspaceId, String code, String targetUrl, Instant expiresAt,
			Instant now) {
		try {
			LinkView created = this.transaction.execute((status) -> this.links
				.saveAndFlush(Link.create(workspaceId, code, targetUrl, expiresAt, now))
				.toView());
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

	private static boolean isCodeConflict(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return CODE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
			}
		}
		return false;
	}

}
