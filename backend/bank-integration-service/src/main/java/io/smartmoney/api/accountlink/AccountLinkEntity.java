package io.smartmoney.api.accountlink;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One bank account assigned to one customer by an administrator.
 *
 * Notifications name the bank account they are for (NCBA AccountNr, KCB
 * creditAccountIdentifier). When that bank and account number match a link,
 * the movement is sent to transactions-service against the customer's account
 * in accounts-service, and it appears on their web dashboard.
 */
@Entity
@Table(name = "account_link", uniqueConstraints = @UniqueConstraint(
        name = "uk_account_link_bank_account", columnNames = {"bank_id", "account_number"}))
public class AccountLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_id", length = 40, nullable = false)
    private String bankId;

    @Column(name = "account_number", length = 64, nullable = false)
    private String accountNumber;

    /** identity-service user id of the customer. */
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    /** The account created for the customer in accounts-service. */
    @Column(name = "account_id", length = 36, nullable = false)
    private String accountId;

    @Column(name = "account_name", length = 150, nullable = false)
    private String accountName;

    /** The admin who made the link. */
    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AccountLinkEntity() {
    }

    public AccountLinkEntity(String bankId, String accountNumber, String userId, String accountId,
                             String accountName, String createdBy) {
        this.bankId = bankId;
        this.accountNumber = accountNumber;
        this.userId = userId;
        this.accountId = accountId;
        this.accountName = accountName;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public String getBankId() { return bankId; }
    public String getAccountNumber() { return accountNumber; }
    public String getUserId() { return userId; }
    public String getAccountId() { return accountId; }
    public String getAccountName() { return accountName; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
