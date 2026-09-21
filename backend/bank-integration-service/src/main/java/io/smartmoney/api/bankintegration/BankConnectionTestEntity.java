package io.smartmoney.api.bankintegration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The last connection test for one bank.
 *
 * It is stored rather than only held in memory so the API column on the
 * integrations page survives a restart. A restart used to show every bank as
 * never tested, which reads like a fault instead of a cold start.
 */
@Entity
@Table(name = "bank_connection_test")
public class BankConnectionTestEntity {

    @Id
    @Column(name = "bank_id", length = 40, nullable = false)
    private String bankId;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    /** The steps as JSON. The interface renders them one by one. */
    @Column(name = "steps", length = 4000)
    private String steps;

    @Column(name = "tested_at", nullable = false)
    private Instant testedAt = Instant.now();

    protected BankConnectionTestEntity() {
    }

    public BankConnectionTestEntity(String bankId, BankConnectionTest test, String steps) {
        this.bankId = bankId;
        this.passed = test.ok();
        this.summary = test.summary();
        this.latencyMs = test.latencyMs();
        this.steps = steps;
        this.testedAt = test.testedAt() == null ? Instant.now() : test.testedAt();
    }

    public String getBankId() {
        return bankId;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getSummary() {
        return summary;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getSteps() {
        return steps;
    }

    public Instant getTestedAt() {
        return testedAt;
    }
}
