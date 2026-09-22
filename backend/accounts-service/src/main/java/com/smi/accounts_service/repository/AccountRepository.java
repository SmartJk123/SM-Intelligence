package com.smi.accounts_service.repository;

import com.smi.accounts_service.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
