package com.smi.transactions_service.repository;

import com.smi.transactions_service.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByAccountIdOrderByTransactionDateDesc(UUID accountId);

    List<Transaction> findByAccountIdAndStatusOrderByTransactionDateDesc(UUID accountId, String status);

    Optional<Transaction> findByProviderReference(String providerReference);

    boolean existsByProviderReference(String providerReference);

    List<Transaction> findByCategoryId(UUID categoryId);

    /** Newest arrivals first across several accounts, for the activity feed. */
    List<Transaction> findByAccountIdInOrderByCreatedAtDesc(java.util.Collection<UUID> accountIds,
                                                            org.springframework.data.domain.Pageable page);

    /** Arrivals after a moment, newest first, for an app polling the activity feed. */
    List<Transaction> findByAccountIdInAndCreatedAtAfterOrderByCreatedAtDesc(
            java.util.Collection<UUID> accountIds, java.time.OffsetDateTime after,
            org.springframework.data.domain.Pageable page);
}
