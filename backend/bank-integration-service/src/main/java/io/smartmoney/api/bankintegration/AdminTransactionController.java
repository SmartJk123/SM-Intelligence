package io.smartmoney.api.bankintegration;

import io.smartmoney.api.accountlink.AccountLinkEntity;
import io.smartmoney.api.accountlink.AccountLinkRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every transaction the banks have reported, newest first, for the admin portal's
 * Transactions page. Each row says which customer's linked account it belongs to
 * and whether it reached their dashboard. Requires an admin token, like the rest
 * of /api/v1/admin.
 */
@RestController
@RequestMapping("/api/v1/admin/transactions")
public class AdminTransactionController {

    private static final int MAX_ROWS = 2000;

    public record AdminTransaction(
            Long id,
            String bankId,
            String reference,
            BigDecimal amount,
            String currency,
            /** CREDIT or DEBIT as the bank stated it, or null. */
            String direction,
            String narration,
            String counterpartyName,
            /** Last four digits only. */
            String accountMasked,
            /** identity-service user id of the customer whose linked account this is, or null. */
            String customerId,
            String accountName,
            Instant bookingDate,
            Instant receivedAt,
            /** DELIVERED (on the customer's dashboard), UNLINKED (no customer has this account) or FAILED. */
            String delivery,
            String deliveryError,
            boolean simulated) {
    }

    private final NormalizedTransactionRepository transactions;
    private final AccountLinkRepository links;

    public AdminTransactionController(NormalizedTransactionRepository transactions, AccountLinkRepository links) {
        this.transactions = transactions;
        this.links = links;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminTransaction> list(@RequestParam(defaultValue = "500") int limit,
                                       @RequestParam(defaultValue = "false") boolean includeDemo) {
        Map<String, AccountLinkEntity> linkByAccount = new HashMap<>();
        for (AccountLinkEntity link : links.findAllByOrderByCreatedAtAsc()) {
            linkByAccount.put(link.getBankId() + ":" + link.getAccountNumber(), link);
        }
        return transactions.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, Math.min(limit, MAX_ROWS))))
                .stream()
                .filter(tx -> includeDemo || !tx.isSimulated())
                .map(tx -> {
                    AccountLinkEntity link = linkByAccount.get(tx.getBankId() + ":" + tx.getAccountNumber());
                    String delivery = tx.getForwardedAt() != null ? "DELIVERED"
                            : tx.getForwardError() != null ? "FAILED"
                            : link == null ? "UNLINKED" : "PENDING";
                    return new AdminTransaction(tx.getId(), tx.getBankId(), tx.getReference(), tx.getAmount(),
                            tx.getCurrency(), normalise(tx.getDirection()), tx.getNarration(), tx.getCounterpartyName(),
                            mask(tx.getAccountNumber()), link == null ? null : link.getUserId(),
                            link == null ? null : link.getAccountName(), tx.getBookingDate(), tx.getCreatedAt(), delivery,
                            tx.getForwardError(), tx.isSimulated());
                })
                .toList();
    }

    private static String normalise(String direction) {
        if (direction == null) return null;
        String value = direction.trim().toLowerCase();
        if (value.startsWith("c") || value.equals("in")) return "CREDIT";
        if (value.startsWith("d") || value.equals("out")) return "DEBIT";
        return null;
    }

    private static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) return null;
        String digits = accountNumber.trim();
        return "•••• " + digits.substring(Math.max(0, digits.length() - 4));
    }
}
