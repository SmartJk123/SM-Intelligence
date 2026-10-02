package io.smartmoney.api.bankintegration;

import io.smartmoney.api.accountlink.AccountLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

/**
 * Receives raw notifications and stores them before anything else happens.
 *
 * The bank must get a fast acknowledgement, so the HTTP layer records the event
 * and returns, and the processing pipeline runs afterwards.
 */
@Service
public class WebhookIngestionService {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngestionService.class);
    private static final int MAX_PAYLOAD = 20000;

    private final WebhookEventRepository repository;
    private final NormalizedTransactionService transactions;
    private final AccountLinkService accountLinks;

    public WebhookIngestionService(
            WebhookEventRepository repository, NormalizedTransactionService transactions,
            AccountLinkService accountLinks) {
        this.repository = repository;
        this.transactions = transactions;
        this.accountLinks = accountLinks;
    }

    @Transactional
    public WebhookEventEntity record(
            String bankId, String externalEventId, Boolean signatureValid, String payload) {

        return store(bankId, externalEventId, signatureValid, payload, false);
    }

    /**
     * Records a demonstration movement. It travels the same validation,
     * deduplication and normalisation path as a bank notification, and is
     * flagged so the delivery figures stay untouched.
     */
    @Transactional
    public WebhookEventEntity recordSimulated(
            String bankId, String externalEventId, String payload) {

        return store(bankId, externalEventId, null, payload, true);
    }

    /**
     * Whether this reference has already been stored. NCBA asks for a duplicate
     * acknowledgement rather than a plain one when a notification is repeated,
     * so the answer is reported explicitly instead of inferred from the insert.
     */
    @Transactional(readOnly = true)
    public boolean alreadyRecorded(String bankId, String externalEventId) {
        return externalEventId != null
                && repository.existsByBankIdAndExternalEventId(bankId, externalEventId);
    }

    private WebhookEventEntity store(
            String bankId, String externalEventId, Boolean signatureValid, String payload, boolean simulated) {
        if (externalEventId != null) {
            var existing = repository.findFirstByBankIdAndExternalEventId(bankId, externalEventId);
            if (existing.isPresent()) {
                log.info("Duplicate {} notification {} ignored", bankId, externalEventId);
                return existing.get();
            }
        }

        WebhookEventEntity saved;
        try {
            saved = repository.saveAndFlush(
                    new WebhookEventEntity(bankId, externalEventId, signatureValid, truncate(payload), simulated));
        } catch (DataIntegrityViolationException duplicate) {
            if (externalEventId != null) {
                return repository.findFirstByBankIdAndExternalEventId(bankId, externalEventId)
                        .orElseThrow(() -> duplicate);
            }
            throw duplicate;
        }
        log.info("Stored {} notification {}, event id {}", bankId, saved.getId(), externalEventId);
        return saved;
    }

    /**
     * The transaction pipeline belongs here: validate, deduplicate on bank and
     * external reference, normalise, persist, reconcile, notify.
     */
    @Async
    @Transactional
    public void process(Long eventId) {
        repository.findById(eventId).ifPresent(event -> {
            try {
                NormalizedTransactionEntity movement = transactions.normalizeAndSave(event);
                accountLinks.deliver(movement);
                event.setProcessingStatus("PROCESSED");
                event.setProcessedAt(Instant.now());
            } catch (Exception error) {
                event.setProcessingStatus("FAILED");
                event.setErrorMessage(truncate(error.getMessage()));
                log.error("Failed to process webhook event {}", eventId, error);
            }
            repository.save(event);
        });
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_PAYLOAD ? value : value.substring(0, MAX_PAYLOAD);
    }
}
