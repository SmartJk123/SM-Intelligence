package io.smartmoney.api.bankintegration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The demonstration account.
 *
 * A bank sandbox cannot show a real account moving, so this generates the two
 * events a demonstration needs: money arriving and money leaving. Every
 * movement travels the same ingestion, deduplication and normalisation path a
 * bank notification uses, and is flagged as simulated, so nothing here changes
 * what a bank is reported to have delivered.
 *
 * The account number and the reference prefix are reserved. Nothing else in the
 * platform may use either one.
 */
@Service
public class DemoTransactionService {

    public static final String REFERENCE_PREFIX = "DEMO-TXN-";
    public static final String DEMO_ACCOUNT_NUMBER = "1000000001";
    public static final String DEMO_ACCOUNT_NAME = "Demo business account";

    private static final List<String> DEMO_BANKS = List.of("kcb", "stanbic", "ncba");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000");
    private static final BigDecimal DEFAULT_AMOUNT = new BigDecimal("1000.00");
    private static final String CURRENCY = "KES";
    private static final int RECENT_LIMIT = 25;

    private final WebhookIngestionService webhooks;
    private final WebhookEventRepository events;
    private final NormalizedTransactionRepository transactions;
    private final ObjectMapper mapper;

    public DemoTransactionService(
            WebhookIngestionService webhooks,
            WebhookEventRepository events,
            NormalizedTransactionRepository transactions,
            ObjectMapper mapper) {
        this.webhooks = webhooks;
        this.events = events;
        this.transactions = transactions;
        this.mapper = mapper;
    }

    /** A movement the demonstration account has just made. */
    public DemoRecorded record(
            String bankId, String direction, BigDecimal amount, String narration, String accountNumber) {

        String bank = bank(bankId);
        String movement = direction(direction);
        BigDecimal value = amount(amount);
        String account = blank(accountNumber) ? DEMO_ACCOUNT_NUMBER : accountNumber.trim();
        String note = blank(narration) ? defaultNarration(movement) : narration.trim();
        String reference = REFERENCE_PREFIX
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Instant bookedAt = Instant.now();

        WebhookEventEntity event = webhooks.recordSimulated(bank, reference, payload(
                reference, account, value, movement, note, bookedAt));
        webhooks.process(event.getId());

        DemoMovement view = new DemoMovement(
                reference, bank, movement, value, CURRENCY, note,
                account, DEMO_ACCOUNT_NAME, bookedAt, true);
        return new DemoRecorded(event.getId(), view);
    }

    /** The latest movements, newest first. */
    public List<DemoMovement> recent() {
        return rows().stream().limit(RECENT_LIMIT).map(this::toView).toList();
    }

    /** Money in, money out and the difference, all measured from stored rows. */
    public DemoSummary summary() {
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal credit = BigDecimal.ZERO;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal creditToday = BigDecimal.ZERO;
        BigDecimal debitToday = BigDecimal.ZERO;
        int credits = 0;
        int debits = 0;
        int movements = 0;

        for (NormalizedTransactionEntity row : rows()) {
            BigDecimal value = row.getAmount() == null ? BigDecimal.ZERO : row.getAmount();
            boolean isCredit = !"Debit".equalsIgnoreCase(row.getDirection());
            boolean today = row.getCreatedAt() != null && !row.getCreatedAt().isBefore(startOfToday);
            movements++;

            if (isCredit) {
                credits++;
                credit = credit.add(value);
                if (today) { creditToday = creditToday.add(value); }
            } else {
                debits++;
                debit = debit.add(value);
                if (today) { debitToday = debitToday.add(value); }
            }
        }

        return new DemoSummary(
                DEMO_ACCOUNT_NUMBER,
                DEMO_ACCOUNT_NAME,
                CURRENCY,
                movements,
                credits,
                debits,
                credit,
                debit,
                credit.subtract(debit),
                creditToday,
                debitToday,
                creditToday.subtract(debitToday));
    }

    /**
     * The movements, selected by the simulated flag and never by the reference.
     *
     * An earlier version selected by the reference prefix, which quietly picked
     * up genuinely signed KCB rehearsals whose references happened to start with
     * the same letters. The flag is the only honest selector.
     */
    private List<NormalizedTransactionEntity> rows() {
        List<NormalizedTransactionEntity> rows = new ArrayList<>();
        for (WebhookEventEntity event : events.findTop100BySimulatedTrueOrderByReceivedAtDesc()) {
            if (event.getExternalEventId() == null) {
                continue;
            }
            transactions
                    .findFirstByBankIdAndExternalEventId(event.getBankId(), event.getExternalEventId())
                    .ifPresent(rows::add);
        }
        return rows;
    }

    private DemoMovement toView(NormalizedTransactionEntity row) {
        return new DemoMovement(
                row.getReference(),
                row.getBankId(),
                row.getDirection(),
                row.getAmount(),
                row.getCurrency(),
                row.getNarration(),
                DEMO_ACCOUNT_NUMBER,
                DEMO_ACCOUNT_NAME,
                row.getBookingDate(),
                true);
    }

    private String payload(
            String reference, String account, BigDecimal amount, String direction,
            String narration, Instant bookedAt) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", reference);
        body.put("bankAccountNumber", account);
        body.put("transactionReference", reference);
        body.put("amount", amount);
        body.put("currency", CURRENCY);
        body.put("creditDebitIndicator", direction);
        body.put("narration", narration);
        body.put("bookingDate", bookedAt.toString());

        try {
            return mapper.writeValueAsString(body);
        } catch (Exception error) {
            throw new IllegalStateException("Could not build the demonstration notification", error);
        }
    }

    private static String defaultNarration(String direction) {
        return "Credit".equals(direction)
                ? "Demo funds received"
                : "Demo payment out";
    }

    private static String bank(String value) {
        String bank = blank(value) ? "kcb" : value.trim().toLowerCase(Locale.ROOT);
        if (!DEMO_BANKS.contains(bank)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Bank has to be one of " + DEMO_BANKS + ", not " + value);
        }
        return bank;
    }

    private static String direction(String value) {
        if (blank(value)) {
            return "Credit";
        }
        String direction = value.trim().toLowerCase(Locale.ROOT);
        if (direction.startsWith("c")) { return "Credit"; }
        if (direction.startsWith("d")) { return "Debit"; }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Direction has to be Credit or Debit");
    }

    private static BigDecimal amount(BigDecimal value) {
        BigDecimal amount = value == null ? DEFAULT_AMOUNT : value;
        if (amount.signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Amount has to be greater than zero");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Amount has to be below " + MAX_AMOUNT.toPlainString());
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** The created event id, and the movement as the interface should show it. */
    public record DemoRecorded(Long eventId, DemoMovement movement) {
    }

    /** One movement on the demonstration account. */
    public record DemoMovement(
            String reference,
            String bankId,
            String direction,
            BigDecimal amount,
            String currency,
            String narration,
            String accountNumber,
            String accountName,
            Instant bookingDate,
            boolean simulated) {
    }

    /** Money in against money out. Every figure is measured from stored rows. */
    public record DemoSummary(
            String accountNumber,
            String accountName,
            String currency,
            int movements,
            int credits,
            int debits,
            BigDecimal creditTotal,
            BigDecimal debitTotal,
            BigDecimal netTotal,
            BigDecimal creditToday,
            BigDecimal debitToday,
            BigDecimal netToday) {
    }
}
