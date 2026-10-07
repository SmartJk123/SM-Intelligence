package com.smi.transactions_service.activity;

import com.smi.transactions_service.domain.Transaction;
import com.smi.transactions_service.invoice.InvoiceIdentity;
import com.smi.transactions_service.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The signed-in user's money in and money out across all their accounts, newest
 * first: the same movements the web dashboard and the admin portal show, so
 * the web app, the admin portal and the mobile app all see one money flow.
 *
 *   GET /api/transactions/activity?limit=50
 *   GET /api/transactions/activity?since=2026-10-07T09:00:00Z   (only what arrived since)
 *   Authorization: Bearer <identity-service token>
 *
 * An app shows notifications by polling with since= set to the newest
 * receivedAt it has seen.
 */
@RestController
@RequestMapping("/api/transactions/activity")
public class ActivityController {

    private static final int MAX_LIMIT = 200;

    /** One movement on one of the user's accounts. */
    public record Activity(
            UUID id,
            UUID accountId,
            String bank,
            String accountName,
            String maskedIdentifier,
            /** CREDIT is money in, DEBIT is money out. */
            String direction,
            BigDecimal amount,
            String currency,
            /** Who sent or received the money, as the bank named them. */
            String counterparty,
            String description,
            String status,
            /** When the bank booked it. */
            OffsetDateTime transactionDate,
            /** When it reached SmartMoney; use the newest one as the next since=. */
            OffsetDateTime receivedAt) {
    }

    private final InvoiceIdentity identity;
    private final AccountDirectory accounts;
    private final TransactionRepository transactions;

    public ActivityController(InvoiceIdentity identity, AccountDirectory accounts, TransactionRepository transactions) {
        this.identity = identity;
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @GetMapping
    public List<Activity> activity(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(defaultValue = "50") int limit) {
        identity.owner(authorization);
        Map<UUID, AccountDirectory.AccountInfo> owned = accounts.accountsOf(authorization).stream()
                .collect(Collectors.toMap(AccountDirectory.AccountInfo::id, Function.identity(), (a, b) -> a));
        if (owned.isEmpty()) {
            return List.of();
        }
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, MAX_LIMIT)));
        List<Transaction> found = since == null
                ? transactions.findByAccountIdInOrderByCreatedAtDesc(owned.keySet(), page)
                : transactions.findByAccountIdInAndCreatedAtAfterOrderByCreatedAtDesc(owned.keySet(), since, page);
        return found.stream().map(tx -> {
            AccountDirectory.AccountInfo account = owned.get(tx.getAccountId());
            return new Activity(tx.getId(), tx.getAccountId(), account.institution(), account.accountName(),
                    account.maskedIdentifier(), tx.getTransactionType(), tx.getAmount(), tx.getCurrency(),
                    tx.getCounterparty(), tx.getDescription(), tx.getStatus(), tx.getTransactionDate(),
                    tx.getCreatedAt());
        }).toList();
    }
}
