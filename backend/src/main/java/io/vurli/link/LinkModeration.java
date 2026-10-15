package io.vurli.link;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;

import io.vurli.link.internal.Link;
import io.vurli.link.internal.LinkCache;
import io.vurli.link.internal.LinkRepository;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Administrator actions on links, across all workspaces. Only the admin panel calls this;
 * it checks the caller and writes the audit log. Every change reaches the redirect for
 * requests that start after the method returns.
 */
@Service
public class LinkModeration {

	static final int MAX_ATTEMPTS = 3;

	private final LinkRepository links;

	private final TransactionTemplate transaction;

	private final LinkCache cache;

	private final Clock clock;

	public LinkModeration(LinkRepository links, PlatformTransactionManager transactionManager, LinkCache cache,
			Clock clock) {
		this.links = links;
		this.transaction = new TransactionTemplate(transactionManager);
		this.cache = cache;
		this.clock = clock;
	}

	/**
	 * Blocks the link regardless of what its owner is doing: unlike owner changes, a block
	 * takes no expected version and retries if an owner change commits in between. Takes
	 * effect for every request that starts after this method returns.
	 * @throws LinkNotFoundException if no link has this id
	 */
	public void block(UUID linkId) {
		change(linkId, Link::block);
	}

	/**
	 * Makes a blocked link active again (K50). Does nothing to a link that is not blocked.
	 * @throws LinkNotFoundException if no link has this id
	 */
	public void unblock(UUID linkId) {
		change(linkId, Link::unblock);
	}

	/**
	 * Blocks every link of a workspace, deleted ones included, so none can be restored.
	 * @return how many links were blocked now
	 */
	public int blockAll(UUID workspaceId) {
		List<String> codes = new ArrayList<>();
		Integer blocked = this.transaction.execute((status) -> {
			codes.addAll(this.links.findUnblockedCodes(workspaceId));
			return this.links.blockAll(workspaceId, this.clock.instant());
		});
		// After commit, like every other change.
		codes.forEach(this.cache::invalidate);
		return (blocked != null) ? blocked : 0;
	}

	/**
	 * Finds links by exact code or by a part of their target, newest first, deleted ones
	 * included.
	 */
	public List<ModeratedLink> search(String query, int limit) {
		String text = (query == null) ? "" : query.trim();
		if (text.isEmpty()) {
			return List.of();
		}
		String pattern = "%" + text.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
				+ "%";
		return this.links.search(text, pattern, PageRequest.of(0, Math.clamp(limit, 1, 100)))
			.stream()
			.map(Link::toModerated)
			.toList();
	}

	public ModeratedLink get(UUID linkId) {
		return this.links.findById(linkId).map(Link::toModerated).orElseThrow(LinkNotFoundException::new);
	}

	/**
	 * Takes no expected version and retries if an owner change commits in between: an
	 * administrator's change must not fail because the owner was editing.
	 */
	private void change(UUID linkId, BiConsumer<Link, Instant> change) {
		for (int attempt = 1;; attempt++) {
			try {
				String code = this.transaction.execute((status) -> {
					Link link = this.links.findById(linkId).orElseThrow(LinkNotFoundException::new);
					change.accept(link, this.clock.instant());
					return this.links.saveAndFlush(link).code();
				});
				this.cache.invalidate(code);
				return;
			}
			catch (OptimisticLockingFailureException ex) {
				if (attempt == MAX_ATTEMPTS) {
					throw ex;
				}
			}
		}
	}

}
