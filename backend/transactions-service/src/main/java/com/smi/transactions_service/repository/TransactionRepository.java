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
}
