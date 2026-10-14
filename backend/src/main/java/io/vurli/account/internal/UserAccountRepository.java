package io.vurli.account.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	/** Expects an already normalized (lowercased) address. */
	Optional<UserAccount> findByEmail(String email);

}
