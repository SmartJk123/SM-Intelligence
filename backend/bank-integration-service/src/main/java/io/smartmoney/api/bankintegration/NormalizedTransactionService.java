package io.smartmoney.api.bankintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class NormalizedTransactionService {

    private static final List<String> AMOUNT_FIELDS = List.of("amount", "transactionAmount", "value");
    private static final List<String> CURRENCY_FIELDS = List.of("currency", "currencyCode");
    private static final List<String> DIRECTION_FIELDS = List.of(
            "creditDebitIndicator", "direction", "transactionType", "type");
    private static final List<String> REFERENCE_FIELDS = List.of(
            "transactionReference", "transactionRef", "reference", "transactionID", "transactionId");
    private static final List<String> NARRATION_FIELDS = List.of("narration", "description", "remarks");
    private static final List<String> DATE_FIELDS = List.of(
            "bookingDate", "transactionDate", "valueDate", "timestamp");
    private static final List<String> COUNTERPARTY_NAME_FIELDS = List.of(
            "customerName", "payerName", "senderName", "payeeName", "name");
    private static final List<String> COUNTERPARTY_PHONE_FIELDS = List.of(
            "phoneNr", "phoneNumber", "mobileNo", "msisdn", "customerMobileNumber");

    // XML aliases, which is the shape NCBA posts. The specification fixes the
    // element names, and the aliases cover the renames banks make between
    // revisions so that a renamed field degrades instead of throwing.
    private static final String[] XML_AMOUNT = {"TransAmount", "Amount", "TransAmt"};
    private static final String[] XML_CURRENCY = {"Currency", "CurrencyCode"};
    private static final String[] XML_REFERENCE = {
            "TransID", "TransId", "TransactionID", "TransactionReference", "TransReference"};
    private static final String[] XML_NARRATIVE = {
            "Narrative", "Narration", "Description", "FtCrNarration", "CrNarration"};
    private static final String[] XML_TIME = {"TransTime", "TransactionTime", "TransDate"};
    private static final String[] XML_ACCOUNT = {"AccountNr", "AccountNumber", "AccountNo"};
    private static final String[] XML_CUSTOMER_NAME = {"CustomerName", "PayerName", "SenderName", "Name"};
    private static final String[] XML_PHONE = {"PhoneNr", "PhoneNumber", "MobileNo", "MSISDN"};
    // KCB names the credited account in creditAccountIdentifier.
    private static final List<String> ACCOUNT_FIELDS = List.of(
            "accountNumber", "creditAccountIdentifier", "bankAccountNumber", "accountNo", "account", "accountId");

    private final NormalizedTransactionRepository repository;
    private final ObjectMapper mapper;
    private final String equityAccountNumber;

    public NormalizedTransactionService(NormalizedTransactionRepository repository, ObjectMapper mapper,
                                        @Value("${smartmoney.equity.account-number:}") String equityAccountNumber) {
        this.repository = repository;
        this.mapper = mapper;
        this.equityAccountNumber = equityAccountNumber;
    }

    /**
     * A notification that cannot be normalised throws IllegalArgumentException.
     * That must not mark the caller's transaction for rollback, or the event's
     * FAILED status and error message would be thrown away with it and the
     * refusal would leave no trace.
     */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public NormalizedTransactionEntity normalizeAndSave(WebhookEventEntity event) {
        if (event.getExternalEventId() == null) {
            throw new IllegalArgumentException("The notification has no external event id");
        }
        var existing = repository.findFirstByBankIdAndExternalEventId(
                event.getBankId(), event.getExternalEventId());
        if (existing.isPresent()) {
            return existing.get();
        }
        return repository.save(draft(event));
    }

    /**
     * Reads either body shape. Every bank posts JSON except NCBA, which posts an
     * XML body, so both are accepted here rather than storing an NCBA
     * notification and failing it a moment later.
     */
    private NormalizedTransactionEntity draft(WebhookEventEntity event) {
        String payload = event.getPayload();
        String trimmed = payload == null ? "" : payload.stripLeading();
        if (trimmed.startsWith("{") && "equity".equals(event.getBankId())) {
            return fromJenga(event, payload);
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return fromJson(event, payload);
        }
        Map<String, String> fields = XmlFields.read(payload);
        if (fields == null) {
            throw new IllegalArgumentException("Notification payload is neither JSON nor XML");
        }
        return fromXml(event, fields);
    }

    private NormalizedTransactionEntity fromJson(WebhookEventEntity event, String payload) {
        try {
            JsonNode root = mapper.readTree(payload);
            BigDecimal amount = decimal(root, AMOUNT_FIELDS);
            if (amount == null || amount.signum() == 0) {
                throw new IllegalArgumentException("Notification amount is missing or invalid");
            }
            String direction = text(root, DIRECTION_FIELDS, null);
            // KCB sends an instant payment notification only after crediting the
            // account, and the payload has no direction field of its own.
            if ((direction == null || direction.isBlank()) && "kcb".equals(event.getBankId()) && amount.signum() > 0) {
                direction = "CREDIT";
            }
            if (amount.signum() < 0) {
                if (direction == null || direction.isBlank()) {
                    direction = "DEBIT";
                }
                amount = amount.abs();
            }
            NormalizedTransactionEntity transaction = new NormalizedTransactionEntity(
                    event.getBankId(), event.getExternalEventId(), amount,
                    text(root, CURRENCY_FIELDS, "KES"),
                    direction,
                    text(root, REFERENCE_FIELDS, event.getExternalEventId()),
                    text(root, NARRATION_FIELDS, null),
                    XmlFields.timestamp(text(root, DATE_FIELDS, null)));
            transaction.setAccountNumber(normalizeAccountNumber(text(root, ACCOUNT_FIELDS, null)));
            transaction.setCounterpartyName(text(root, COUNTERPARTY_NAME_FIELDS, null));
            transaction.setCounterpartyPhone(text(root, COUNTERPARTY_PHONE_FIELDS, null));
            transaction.setSimulated(event.isSimulated());
            return transaction;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Notification payload is not valid JSON", error);
        }
    }

    /**
     * Equity's Jenga Instant Payment Notification nests the payment:
     * {"customer": {name, mobileNumber, reference}, "transaction": {date,
     * reference, amount, currency, status, remarks, ...}, "bank": {reference,
     * transactionType, account}}. transactionType is C for a credit. A FAILED
     * payment moved no money, so it is refused here and never delivered.
     * bank.account may be null, in which case the payment belongs to the
     * configured Equity account (EQUITY_ACCOUNT_NUMBER).
     */
    private NormalizedTransactionEntity fromJenga(WebhookEventEntity event, String payload) {
        JsonNode root;
        try {
            root = mapper.readTree(payload);
        } catch (Exception error) {
            throw new IllegalArgumentException("Notification payload is not valid JSON", error);
        }
        JsonNode transaction = root.path("transaction");
        String status = transaction.path("status").asText("");
        if (!status.isBlank() && !"SUCCESS".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Equity reported this payment as " + status
                    + (transaction.path("remarks").asText("").isBlank() ? "" : " (" + transaction.path("remarks").asText() + ")")
                    + ", so no money moved and nothing is recorded");
        }
        BigDecimal amount = parse(transaction.path("amount").asText(""));
        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException("Notification amount is missing or invalid");
        }
        String type = root.at("/bank/transactionType").asText("").trim().toUpperCase();
        String direction = type.startsWith("D") ? "DEBIT" : "CREDIT";
        String reference = transaction.path("reference").asText("");
        if (reference.isBlank()) {
            reference = root.at("/bank/reference").asText(event.getExternalEventId());
        }
        String narration = firstNonBlank(transaction.path("remarks").asText(""),
                transaction.path("billNumber").asText(""), transaction.path("additionalInfo").asText(""));
        NormalizedTransactionEntity movement = new NormalizedTransactionEntity(
                event.getBankId(), event.getExternalEventId(), amount.abs(),
                transaction.path("currency").asText("").isBlank() ? "KES" : transaction.path("currency").asText(),
                direction, reference, narration,
                XmlFields.timestamp(transaction.path("date").asText(null)));
        String account = root.at("/bank/account").asText("");
        movement.setAccountNumber(normalizeAccountNumber(account.isBlank() ? equityAccountNumber : account));
        movement.setCounterpartyName(blankToNull(root.at("/customer/name").asText("")));
        movement.setCounterpartyPhone(blankToNull(root.at("/customer/mobileNumber").asText("")));
        return movement;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * TransAmount carries the direction in its sign, negative for a debit, so the
     * stored amount is the magnitude and the direction is kept as its own field.
     */
    private NormalizedTransactionEntity fromXml(WebhookEventEntity event, Map<String, String> fields) {
        BigDecimal amount = parse(XmlFields.first(fields, XML_AMOUNT));
        if (amount == null) {
            throw new IllegalArgumentException("Notification amount is missing or invalid");
        }
        String direction = amount.signum() < 0 ? "Debit" : "Credit";
        NormalizedTransactionEntity transaction = new NormalizedTransactionEntity(
                event.getBankId(),
                event.getExternalEventId(),
                amount.abs(),
                orDefault(XmlFields.first(fields, XML_CURRENCY), "KES"),
                direction,
                orDefault(XmlFields.first(fields, XML_REFERENCE), event.getExternalEventId()),
                XmlFields.first(fields, XML_NARRATIVE),
                XmlFields.timestamp(XmlFields.first(fields, XML_TIME)));
        transaction.setAccountNumber(normalizeAccountNumber(XmlFields.first(fields, XML_ACCOUNT)));
        transaction.setCounterpartyName(XmlFields.first(fields, XML_CUSTOMER_NAME));
        transaction.setCounterpartyPhone(XmlFields.first(fields, XML_PHONE));
        transaction.setSimulated(event.isSimulated());
        return transaction;
    }

    /**
     * The form account numbers are stored and matched in: spaces and dashes
     * removed, so "1004 906 164" and "1004906164" link to the same customer.
     */
    public static String normalizeAccountNumber(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\s-]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static BigDecimal parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static BigDecimal decimal(JsonNode root, List<String> fields) {
        String value = text(root, fields, null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static String text(JsonNode root, List<String> fields, String fallback) {
        for (String field : fields) {
            JsonNode value = root.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return fallback;
    }

}
