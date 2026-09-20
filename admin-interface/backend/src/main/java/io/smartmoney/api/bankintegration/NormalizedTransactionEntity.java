package io.smartmoney.api.bankintegration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "normalized_transaction", uniqueConstraints = @UniqueConstraint(
        name = "uk_normalized_transaction_bank_external",
        columnNames = {"bank_id", "external_event_id"}))
public class NormalizedTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_id", length = 40, nullable = false)
    private String bankId;

    @Column(name = "external_event_id", length = 120, nullable = false)
    private String externalEventId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "direction", length = 20)
    private String direction;

    @Column(name = "reference", length = 160)
    private String reference;

    @Column(name = "narration", length = 500)
    private String narration;

    @Column(name = "booking_date")
    private Instant bookingDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected NormalizedTransactionEntity() {
    }

    public NormalizedTransactionEntity(
            String bankId, String externalEventId, BigDecimal amount, String currency,
            String direction, String reference, String narration, Instant bookingDate) {
        this.bankId = bankId;
        this.externalEventId = externalEventId;
        this.amount = amount;
        this.currency = currency;
        this.direction = direction;
        this.reference = reference;
        this.narration = narration;
        this.bookingDate = bookingDate;
    }

    public Long getId() { return id; }
    public String getBankId() { return bankId; }
    public String getExternalEventId() { return externalEventId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDirection() { return direction; }
    public String getReference() { return reference; }
    public String getNarration() { return narration; }
    public Instant getBookingDate() { return bookingDate; }
    public Instant getCreatedAt() { return createdAt; }
}
