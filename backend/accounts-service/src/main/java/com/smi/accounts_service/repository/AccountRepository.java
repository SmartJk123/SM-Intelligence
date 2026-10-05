package com.smi.accounts_service.repository;

import com.smi.accounts_service.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserId(UUID userId);

    List<Account> findByUserIdAndAccountStatus(UUID userId, String accountStatus);

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    Optional<Account> findByInstitutionAndProviderAccountId(String institution, String providerAccountId);

    boolean existsByInstitutionAndProviderAccountId(String institution, String providerAccountId);

    List<Account> findByProviderAccountId(String providerAccountId);

    /** Owners of self-entered accounts at one bank, for matching fingerprints made before they were owner-free. */
    @Query("SELECT DISTINCT a.userId FROM Account a WHERE a.institution = :institution AND a.dataSource = 'MANUAL'")
    List<UUID> findManualOwnersByInstitution(@Param("institution") String institution);

    /**
     * Moves the balance by delta in one statement, so two movements arriving at
     * the same time both land instead of the second silently overwriting the
     * first's effect (which a read-then-write from the caller would risk).
     */
    @Modifying
    @Query("UPDATE Account a SET a.availableBalance = a.availableBalance + :delta, "
            + "a.ledgerBalance = a.ledgerBalance + :delta, a.lastUpdated = :now WHERE a.id = :id")
    int adjustAvailableBalance(@Param("id") UUID id, @Param("delta") BigDecimal delta, @Param("now") OffsetDateTime now);
}
