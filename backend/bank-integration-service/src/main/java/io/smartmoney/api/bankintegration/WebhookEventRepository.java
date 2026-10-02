package io.smartmoney.api.bankintegration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, Long> {

    List<WebhookEventEntity> findTop50ByOrderByReceivedAtDesc();

    long countByReceivedAtAfter(Instant after);

    long countByProcessingStatus(String processingStatus);

    Optional<WebhookEventEntity> findFirstByBankIdOrderByReceivedAtDesc(String bankId);

    Optional<WebhookEventEntity> findFirstByBankIdAndExternalEventId(String bankId, String externalEventId);

    /** Used for the duplicate acknowledgement some banks require. */
    boolean existsByBankIdAndExternalEventId(String bankId, String externalEventId);

    long countByBankIdAndProcessingStatusAndReceivedAtAfter(
            String bankId, String processingStatus, Instant after);

    long countByBankId(String bankId);

    long countByBankIdAndReceivedAtAfter(String bankId, Instant after);

    List<WebhookEventEntity> findTop20ByBankIdOrderByReceivedAtDesc(String bankId);

    // Platform wide queries, used by the statistics view.

    List<WebhookEventEntity> findByReceivedAtAfterOrderByReceivedAtAsc(Instant after);

    List<WebhookEventEntity> findTop25ByOrderByReceivedAtDesc();

    // Queries that leave the demonstration account out. "What the bank has
    // actually delivered" has to mean exactly that, so a demonstration cannot
    // change a single delivery figure.

    List<WebhookEventEntity> findBySimulatedFalse();

    Optional<WebhookEventEntity> findFirstByBankIdAndSimulatedFalseOrderByReceivedAtDesc(String bankId);

    long countByBankIdAndSimulatedFalse(String bankId);

    long countByBankIdAndReceivedAtAfterAndSimulatedFalse(String bankId, Instant after);

    long countByBankIdAndProcessingStatusAndReceivedAtAfterAndSimulatedFalse(
            String bankId, String processingStatus, Instant after);

    /** The demonstration account. Selected by the flag, never by a reference. */
    List<WebhookEventEntity> findTop100BySimulatedTrueOrderByReceivedAtDesc();
}
