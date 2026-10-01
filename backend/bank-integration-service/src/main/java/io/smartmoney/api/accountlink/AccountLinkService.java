package io.smartmoney.api.accountlink;

import io.smartmoney.api.bankintegration.NormalizedTransactionEntity;
import io.smartmoney.api.bankintegration.NormalizedTransactionRepository;
import io.smartmoney.api.bankintegration.NormalizedTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Assigns bank accounts to customers and delivers each account's movements to
 * the customer's web dashboard.
 *
 * Delivery happens when a notification is processed, again for every earlier
 * movement when a link is made (so notifications that arrived before the
 * customer was linked are not lost), and every five minutes for anything a
 * downstream outage left behind. transactions-service rejects a repeated
 * provider reference, so a movement is never recorded twice.
 */
@Service
public class AccountLinkService {

    private static final Logger log = LoggerFactory.getLogger(AccountLinkService.class);

    /** The banks a customer account can be linked for, and the name shown on the dashboard. */
    static final Map<String, String> INSTITUTIONS = Map.of(
            "ncba", "NCBA",
            "kcb", "KCB",
            "stanbic", "Stanbic",
            "equity", "Equity");

    private final AccountLinkRepository links;
    private final NormalizedTransactionRepository transactions;
    private final PlatformServicesClient platform;

    public AccountLinkService(AccountLinkRepository links, NormalizedTransactionRepository transactions,
                              PlatformServicesClient platform) {
        this.links = links;
        this.transactions = transactions;
        this.platform = platform;
    }

    public List<AccountLinkEntity> list(String userId) {
        return userId == null || userId.isBlank()
                ? links.findAllByOrderByCreatedAtAsc()
                : links.findByUserIdOrderByCreatedAtAsc(userId);
    }

    public AccountLinkEntity get(Long id) {
        return links.findById(id).orElseThrow(() -> new AccountLinkException(HttpStatus.NOT_FOUND, "Link not found"));
    }

    /** Movements on the linked account still waiting for delivery. */
    public int pendingCount(AccountLinkEntity link) {
        return pending(link).size();
    }

    /** The most recent delivery error on the linked account, or null. */
    public String lastError(AccountLinkEntity link) {
        return pending(link).stream()
                .map(NormalizedTransactionEntity::getForwardError)
                .filter(error -> error != null && !error.isBlank())
                .reduce((first, second) -> second)
                .orElse(null);
    }

    public AccountLinkEntity link(String bankId, String accountNumber, String userId, String accountName,
                                  String adminId) {
        return link(bankId, accountNumber, userId, accountName, adminId, null);
    }

    /**
     * @param existingAccountId When the caller already holds an accounts-service
     *                          account for this customer (for example, one they
     *                          created themselves during onboarding), pass its id
     *                          here to link to it directly instead of creating a
     *                          new one. Needed because a customer's self-entered
     *                          account is stored under a hashed fingerprint, not
     *                          the plain account number ensureAccount matches on,
     *                          so the two would otherwise never reconcile.
     */
    public AccountLinkEntity link(String bankId, String accountNumber, String userId, String accountName,
                                  String adminId, String existingAccountId) {
        String bank = bankId == null ? "" : bankId.trim().toLowerCase();
        String institution = INSTITUTIONS.get(bank);
        if (institution == null) {
            throw new AccountLinkException(HttpStatus.BAD_REQUEST, "Unknown bank: " + bankId);
        }
        String number = NormalizedTransactionService.normalizeAccountNumber(accountNumber);
        if (number == null || !number.matches("[A-Za-z0-9]{4,34}")) {
            throw new AccountLinkException(HttpStatus.BAD_REQUEST,
                    "Account number must be 4 to 34 letters or digits");
        }
        try {
            UUID.fromString(userId == null ? "" : userId.trim());
        } catch (IllegalArgumentException invalid) {
            throw new AccountLinkException(HttpStatus.BAD_REQUEST, "userId must be a user id from identity-service");
        }
        String name = accountName == null || accountName.isBlank()
                ? institution + " " + number.substring(Math.max(0, number.length() - 4))
                : accountName.trim();
        if (name.length() > 150) {
            throw new AccountLinkException(HttpStatus.BAD_REQUEST, "Account name must be at most 150 characters");
        }

        Optional<AccountLinkEntity> existing = links.findByBankIdAndAccountNumber(bank, number);
        if (existing.isPresent()) {
            throw new AccountLinkException(HttpStatus.CONFLICT, existing.get().getUserId().equals(userId.trim())
                    ? "This account is already linked to this customer"
                    : "This account is already linked to another customer. Remove that link first.");
        }

        String accountId = existingAccountId == null || existingAccountId.isBlank()
                ? platform.ensureAccount(userId.trim(), institution, number, name)
                : existingAccountId.trim();
        AccountLinkEntity saved = links.save(
                new AccountLinkEntity(bank, number, userId.trim(), accountId, name, adminId));
        log.info("Linked {} account ending {} to user {}", bank, last4(number), saved.getUserId());
        deliverPending(saved);
        return saved;
    }

    /** Removes the link and closes the dashboard account. Recorded history is kept. */
    public void unlink(Long id) {
        AccountLinkEntity link = get(id);
        platform.closeAccount(link.getAccountId());
        links.delete(link);
        log.info("Unlinked {} account ending {} from user {}", link.getBankId(), last4(link.getAccountNumber()),
                link.getUserId());
    }

    /** Called for every processed notification. Never throws: delivery is retried later. */
    public void deliver(NormalizedTransactionEntity movement) {
        if (movement.getAccountNumber() == null || movement.getForwardedAt() != null || movement.isSimulated()) {
            return;
        }
        links.findByBankIdAndAccountNumber(movement.getBankId(), movement.getAccountNumber())
                .ifPresent(link -> send(link, movement));
    }

    /** Sends every waiting movement on one link. @return how many are still waiting. */
    public int deliverPending(AccountLinkEntity link) {
        for (NormalizedTransactionEntity movement : pending(link)) {
            send(link, movement);
        }
        return pendingCount(link);
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT5M")
    public void retryPending() {
        for (AccountLinkEntity link : links.findAll()) {
            if (!pending(link).isEmpty()) {
                deliverPending(link);
            }
        }
    }

    private List<NormalizedTransactionEntity> pending(AccountLinkEntity link) {
        return transactions.findByBankIdAndAccountNumberAndForwardedAtIsNullAndSimulatedFalseOrderByCreatedAtAsc(
                link.getBankId(), link.getAccountNumber());
    }

    private void send(AccountLinkEntity link, NormalizedTransactionEntity movement) {
        try {
            String type = transactionType(movement.getDirection());
            if (movement.getAmount() == null || movement.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("The amount is zero, which transactions-service does not accept");
            }
            Instant bookedAt = movement.getBookingDate() != null ? movement.getBookingDate() : movement.getCreatedAt();
            platform.recordTransaction(link.getAccountId(), movement.getAmount(), movement.getCurrency(), type,
                    link.getBankId() + ":" + movement.getReference(), movement.getNarration(), bookedAt);
            movement.setForwardedAt(Instant.now());
            movement.setForwardError(null);
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            movement.setForwardError(message.length() > 500 ? message.substring(0, 500) : message);
            log.warn("Could not deliver {} movement {} to the customer dashboard: {}",
                    movement.getBankId(), movement.getReference(), message);
        }
        transactions.save(movement);
    }

    /** Credit or debit from the direction the bank gave, in whatever spelling it used. */
    private static String transactionType(String direction) {
        String value = direction == null ? "" : direction.trim().toUpperCase();
        if (value.startsWith("C")) {
            return "CREDIT";
        }
        if (value.startsWith("D")) {
            return "DEBIT";
        }
        throw new IllegalStateException("The notification does not say whether it is a credit or a debit");
    }

    private static String last4(String number) {
        return number.substring(Math.max(0, number.length() - 4));
    }
}
