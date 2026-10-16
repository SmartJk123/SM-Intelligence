package com.smi.investments_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** What an investment was worth on one day. The schema allows one row per investment per day. */
@Entity
@Table(name = "investment_valuations")
public class InvestmentValuation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "investment_id", nullable = false, updatable = false)
    private UUID investmentId;

    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    @Column(name = "current_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentValue;

    /** PROVIDER, MARKET_FEED, CALCULATION or MANUAL. Values typed by the customer are MANUAL. */
    @Column(name = "valuation_source", nullable = false)
    private String valuationSource = "MANUAL";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InvestmentValuation() {
    }

    public InvestmentValuation(UUID investmentId, LocalDate valuationDate, BigDecimal currentValue) {
        this.investmentId = investmentId;
        this.valuationDate = valuationDate;
        this.currentValue = currentValue;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getInvestmentId() { return investmentId; }
    public LocalDate getValuationDate() { return valuationDate; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }
}
