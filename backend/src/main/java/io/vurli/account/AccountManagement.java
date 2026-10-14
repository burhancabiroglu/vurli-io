package io.vurli.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import io.vurli.account.internal.AccountMailer;
import io.vurli.account.internal.EmailAddress;
import io.vurli.account.internal.EmailTokens;
import io.vurli.account.internal.EmailTokens.Purpose;
import io.vurli.account.internal.PasswordPolicy;
import io.vurli.account.internal.UserAccount;
import io.vurli.account.internal.UserAccountRepository;
import io.vurli.account.internal.Workspace;
import io.vurli.account.internal.WorkspaceRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registration, email verification and password reset.
 * <p>
 * None of the public outcomes reveal whether an address has an account: registering an
 * existing address and asking to reset an unknown one look exactly like the normal case
 * to the caller. Only the mailbox owner learns the difference.
 */
@Service
public class AccountManagement {

	static final Duration VERIFICATION_TTL = Duration.ofHours(24);

	static final Duration RESET_TTL = Duration.ofHours(1);

	static final Duration EMAIL_COOLDOWN = Duration.ofSeconds(60);

	private static final String EMAIL_CONSTRAINT = "uk_users_email";

	private static final Logger logger = LoggerFactory.getLogger(AccountManagement.class);

	private final UserAccountRepository users;

	private final WorkspaceRepository workspaces;

	private final EmailTokens tokens;

	private final AccountMailer mailer;

	private final PasswordEncoder passwordEncoder;

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public AccountManagement(UserAccountRepository users, WorkspaceRepository workspaces, EmailTokens tokens,
			AccountMailer mailer, PasswordEncoder passwordEncoder,
			FindByIndexNameSessionRepository<? extends Session> sessions,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.users = users;
		this.workspaces = workspaces;
		this.tokens = tokens;
		this.mailer = mailer;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/**
	 * Creates an account with its personal workspace and sends a verification email. If
	 * the address already has an account, its owner gets an email instead; the caller
	 * cannot tell the two apart.
	 * @throws InvalidEmailException if the address is malformed
	 * @throws WeakPasswordException if the password breaks the length rule
	 */
	public void register(String email, String password, Locale locale) {
		String address = EmailAddress.normalize(email);
		PasswordPolicy.check(password);
		// Hashed even when the account exists, so response time does not reveal it.
		String passwordHash = this.passwordEncoder.encode(password);

		Optional<UserAccount> existing = this.users.findByEmail(address);
		if (existing.isPresent()) {
			notifyExisting(existing.get());
			return;
		}
		Instant now = this.clock.instant();
		try {
			Registered registered = this.transaction.execute((status) -> {
				UserAccount user = this.users.saveAndFlush(
						UserAccount.register(address, passwordHash, SupportedLocale.of(locale), now));
				this.workspaces.save(Workspace.personal(user.id(), now));
				String token = this.tokens.issue(user.id(), Purpose.VERIFY_EMAIL, VERIFICATION_TTL, now);
				return new Registered(user, token);
			});
			sendQuietly(() -> this.mailer.sendVerification(registered.user(), registered.token()), registered.user());
		}
		catch (DataIntegrityViolationException ex) {
			if (!isEmailConflict(ex)) {
				throw ex;
			}
			// A concurrent registration for the same address won: treat it as existing.
			this.users.findByEmail(address).ifPresent(this::notifyExisting);
		}
	}

	/**
	 * @throws InvalidTokenException if the token is unknown, expired or already used
	 */
	public void verifyEmail(String token) {
		Instant now = this.clock.instant();
		this.transaction.executeWithoutResult((status) -> {
			UUID userId = this.tokens.consume(token, Purpose.VERIFY_EMAIL, now)
				.orElseThrow(InvalidTokenException::new);
			UserAccount user = this.users.findById(userId).orElseThrow(InvalidTokenException::new);
			user.markEmailVerified(now);
		});
	}

	/**
	 * Sends a fresh verification email, at most once per {@link #EMAIL_COOLDOWN}. Does
	 * nothing for an already verified account.
	 * @throws TooManyEmailsException if an email was sent moments ago
	 */
	public void resendVerification(UUID userId) {
		UserAccount user = this.users.findById(userId).orElseThrow();
		if (user.isEmailVerified()) {
			return;
		}
		Instant now = this.clock.instant();
		Optional<Instant> last = this.tokens.lastIssuedAt(userId, Purpose.VERIFY_EMAIL);
		if (last.isPresent() && last.get().plus(EMAIL_COOLDOWN).isAfter(now)) {
			throw new TooManyEmailsException(Duration.between(now, last.get().plus(EMAIL_COOLDOWN)));
		}
		String token = this.transaction
			.execute((status) -> this.tokens.issue(userId, Purpose.VERIFY_EMAIL, VERIFICATION_TTL, now));
		sendQuietly(() -> this.mailer.sendVerification(user, token), user);
	}

	/**
	 * Sends a reset link if the address has an account. Unknown addresses and repeated
	 * requests within the cooldown are silently ignored, so the caller learns nothing.
	 * @throws InvalidEmailException if the address is malformed
	 */
	public void requestPasswordReset(String email) {
		String address = EmailAddress.normalize(email);
		Optional<UserAccount> found = this.users.findByEmail(address);
		if (found.isEmpty()) {
			return;
		}
		UserAccount user = found.get();
		Instant now = this.clock.instant();
		Optional<Instant> last = this.tokens.lastIssuedAt(user.id(), Purpose.RESET_PASSWORD);
		if (last.isPresent() && last.get().plus(EMAIL_COOLDOWN).isAfter(now)) {
			return;
		}
		String token = this.transaction
			.execute((status) -> this.tokens.issue(user.id(), Purpose.RESET_PASSWORD, RESET_TTL, now));
		sendQuietly(() -> this.mailer.sendPasswordReset(user, token), user);
	}

	/**
	 * Sets a new password, invalidates every other reset link and signs the account out
	 * everywhere. Following the link also proves control of the mailbox, so the address
	 * counts as verified.
	 * @throws WeakPasswordException if the password breaks the length rule
	 * @throws InvalidTokenException if the token is unknown, expired or already used
	 */
	public void resetPassword(String token, String newPassword) {
		PasswordPolicy.check(newPassword);
		String passwordHash = this.passwordEncoder.encode(newPassword);
		Instant now = this.clock.instant();
		String email = this.transaction.execute((status) -> {
			UUID userId = this.tokens.consume(token, Purpose.RESET_PASSWORD, now)
				.orElseThrow(InvalidTokenException::new);
			UserAccount user = this.users.findById(userId).orElseThrow(InvalidTokenException::new);
			user.changePassword(passwordHash, now);
			user.markEmailVerified(now);
			this.tokens.revokeAll(userId, Purpose.RESET_PASSWORD, now);
			return user.email();
		});
		this.sessions.findByPrincipalName(email).keySet().forEach(this.sessions::deleteById);
	}

	public boolean isEmailVerified(UUID userId) {
		return this.users.findById(userId).map(UserAccount::isEmailVerified).orElse(false);
	}

	public AccountView view(VurliUser user) {
		UserAccount account = this.users.findById(user.userId()).orElseThrow();
		return new AccountView(account.id(), account.email(), account.isEmailVerified(), account.locale(),
				user.workspaceId());
	}

	/**
	 * An unverified owner most likely lost the first email; a verified one gets a hint to
	 * sign in or reset the password.
	 */
	private void notifyExisting(UserAccount user) {
		if (user.isEmailVerified()) {
			sendQuietly(() -> this.mailer.sendAlreadyRegistered(user), user);
		}
		else {
			try {
				resendVerification(user.id());
			}
			catch (TooManyEmailsException ex) {
				// Same response as always; the owner already has a fresh email.
			}
		}
	}

	/**
	 * Email delivery problems must not change the response, or they would reveal which
	 * branch ran. The user can ask for another email later.
	 */
	private void sendQuietly(Runnable send, UserAccount user) {
		try {
			send.run();
		}
		catch (MailException ex) {
			logger.warn("Could not send account email to user {}", user.id(), ex);
		}
	}

	private static boolean isEmailConflict(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return EMAIL_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
			}
		}
		return false;
	}

	private record Registered(UserAccount user, String token) {
	}

}
