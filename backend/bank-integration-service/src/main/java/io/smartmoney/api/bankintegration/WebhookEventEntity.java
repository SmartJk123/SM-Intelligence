package io.smartmoney.api.bankintegration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "webhook_event", uniqueConstraints = @jakarta.persistence.UniqueConstraint(
        name = "uk_webhook_event_bank_external", columnNames = {"bank_id", "external_event_id"}))
public class WebhookEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_id", length = 40, nullable = false)
    private String bankId;

    @Column(name = "external_event_id", length = 120)
    private String externalEventId;

    @Column(name = "signature_valid")
    private Boolean signatureValid;

    @Column(name = "payload", nullable = false, length = 20000)
    private String payload;

    @Column(name = "processing_status", length = 30, nullable = false)
    private String processingStatus = "RECEIVED";

    /**
     * True for a movement created by the demonstration account rather than
     * delivered by a bank. The column exists so a demonstration never inflates
     * what a bank has actually sent.
     */
    // The default matters. The table already holds received notifications, and
    // an added NOT NULL column with no default cannot be applied to rows that
    // are already there. Without it the schema update is skipped, and then every
    // query that filters on the column fails at run time.
    @Column(name = "simulated", nullable = false, columnDefinition = "boolean default false")
    private boolean simulated = false;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    protected WebhookEventEntity() {
    }

    public WebhookEventEntity(String bankId, String externalEventId, Boolean signatureValid, String payload) {
        this(bankId, externalEventId, signatureValid, payload, false);
    }

    public WebhookEventEntity(
            String bankId, String externalEventId, Boolean signatureValid, String payload, boolean simulated) {
        this.bankId = bankId;
        this.externalEventId = externalEventId;
        this.signatureValid = signatureValid;
        this.payload = payload;
        this.simulated = simulated;
    }

    public Long getId() {
        return id;
    }

    public String getBankId() {
        return bankId;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public Boolean getSignatureValid() {
        return signatureValid;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public String getPayload() {
        return payload;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
