package io.smartmoney.api.bankintegration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NormalizedTransactionRepository extends JpaRepository<NormalizedTransactionEntity, Long> {

    Optional<NormalizedTransactionEntity> findFirstByBankIdAndExternalEventId(
            String bankId, String externalEventId);

    /** Movements found by reference, for the rehearsal and reporting paths. */
    List<NormalizedTransactionEntity> findByReferenceStartingWithOrderByCreatedAtDesc(String prefix);

    /** Real movements on one account not yet sent to transactions-service, oldest first. */
    List<NormalizedTransactionEntity> findByBankIdAndAccountNumberAndForwardedAtIsNullAndSimulatedFalseOrderByCreatedAtAsc(
            String bankId, String accountNumber);
}
