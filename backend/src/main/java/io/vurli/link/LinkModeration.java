package io.vurli.link;

import java.time.Clock;
import java.util.UUID;

import io.vurli.link.internal.Link;
import io.vurli.link.internal.LinkCache;
import io.vurli.link.internal.LinkRepository;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Administrator actions on links, across all workspaces. Who may call this is decided by
 * the abuse module (M3); audit logging arrives there too.
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
		for (int attempt = 1;; attempt++) {
			try {
				String code = this.transaction.execute((status) -> {
					Link link = this.links.findById(linkId).orElseThrow(LinkNotFoundException::new);
					link.block(this.clock.instant());
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
