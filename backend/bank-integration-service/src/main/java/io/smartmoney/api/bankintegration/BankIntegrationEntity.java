package io.smartmoney.api.bankintegration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "bank_integration")
public class BankIntegrationEntity {

    @Id
    @Column(name = "bank_id", length = 40, nullable = false)
    private String bankId;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", length = 20, nullable = false)
    private BankEnvironment environment = BankEnvironment.SANDBOX;

    @Column(name = "account_number", length = 40)
    private String accountNumber;

    @Column(name = "api_timeout_seconds", nullable = false)
    private int apiTimeoutSeconds = 30;

    @Column(name = "retry_attempts", nullable = false)
    private int retryAttempts = 3;

    @Column(name = "retry_delay_seconds", nullable = false)
    private int retryDelaySeconds = 5;

    @Column(name = "signature_verification", nullable = false)
    private boolean signatureVerification = true;

    @Column(name = "automatic_retry", nullable = false)
    private boolean automaticRetry = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected BankIntegrationEntity() {
    }

    public BankIntegrationEntity(String bankId) {
        this.bankId = bankId;
    }

    public String getBankId() {
        return bankId;
    }

    public BankEnvironment getEnvironment() {
        return environment;
    }

    public void setEnvironment(BankEnvironment environment) {
        this.environment = environment;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public int getApiTimeoutSeconds() {
        return apiTimeoutSeconds;
    }

    public void setApiTimeoutSeconds(int apiTimeoutSeconds) {
        this.apiTimeoutSeconds = apiTimeoutSeconds;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public int getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    public void setRetryDelaySeconds(int retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public boolean isSignatureVerification() {
        return signatureVerification;
    }

    public void setSignatureVerification(boolean signatureVerification) {
        this.signatureVerification = signatureVerification;
    }

    public boolean isAutomaticRetry() {
        return automaticRetry;
    }

    public void setAutomaticRetry(boolean automaticRetry) {
        this.automaticRetry = automaticRetry;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
