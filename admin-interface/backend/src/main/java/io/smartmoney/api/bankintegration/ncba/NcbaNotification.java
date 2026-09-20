package io.smartmoney.api.bankintegration.ncba;

import io.smartmoney.api.bankintegration.XmlFields;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * One NCBA account level push notification, read out of the SOAP body.
 *
 * The element names are fixed by the specification: NCBA states that the
 * innermost tags cannot be altered, and that a custom body may replace the
 * envelope as long as those tags survive. Aliases are still accepted, because
 * banks rename fields between revisions and a renamed field should degrade to
 * an empty value rather than an exception.
 */
public record NcbaNotification(
        String user,
        String password,
        String hashVal,
        String transType,
        String transId,
        String transTime,
        String transAmount,
        String accountNr,
        String narrative,
        String phoneNr,
        String customerName,
        String status,
        String ftCrNarration,
        Map<String, String> fields) {

    /** @return the parsed notification, or null when the body is not XML. */
    public static NcbaNotification parse(String xml) {
        Map<String, String> fields = XmlFields.read(xml);
        if (fields == null) {
            return null;
        }
        return new NcbaNotification(
                value(fields, "User", "Username", "UserName"),
                value(fields, "Password", "Passwd"),
                value(fields, "HashVal", "Hash"),
                value(fields, "TransType", "TransactionType", "TranType"),
                value(fields, "TransID", "TransId", "TransactionID", "TransactionId",
                        "TransReference", "TransactionReference"),
                value(fields, "TransTime", "TransactionTime", "TransDate"),
                value(fields, "TransAmount", "Amount", "TransAmt"),
                value(fields, "AccountNr", "AccountNumber", "AccountNo"),
                value(fields, "Narrative", "Narration", "Description", "Details"),
                value(fields, "PhoneNr", "PhoneNumber", "MobileNo", "MSISDN"),
                value(fields, "CustomerName", "PayerName", "SenderName", "Name"),
                value(fields, "Status", "TransactionStatus"),
                value(fields, "FtCrNarration", "CrNarration"),
                fields);
    }

    private static String value(Map<String, String> fields, String... names) {
        String found = XmlFields.first(fields, names);
        return found == null ? "" : found;
    }

    /**
     * The transaction amount, signed the way NCBA sends it: negative is a debit
     * and positive is a credit. Null when the field is missing or not a number.
     */
    public BigDecimal amount() {
        if (transAmount == null || transAmount.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(transAmount.replace(",", "").trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /** The direction the platform stores, derived from the sign of the amount. */
    public String direction() {
        BigDecimal value = amount();
        if (value == null) {
            return null;
        }
        return value.signum() < 0 ? "Debit" : "Credit";
    }

    /** The reference used for duplicate suppression, which is TransID. */
    public String reference() {
        return transId == null || transId.isBlank() ? null : transId;
    }

    /** Narrative, falling back to the free text narration when it is empty. */
    public String narration() {
        if (narrative != null && !narrative.isBlank()) {
            return narrative;
        }
        return ftCrNarration == null || ftCrNarration.isBlank() ? null : ftCrNarration;
    }

    /** TransTime is YYMMDDhhmm with no zone, read as East Africa Time. */
    public Instant bookedAt() {
        return XmlFields.timestamp(transTime);
    }

    /** Short description for a log line. Never includes the password or the hash. */
    public String describe() {
        return "reference=" + reference()
                + " type=" + transType
                + " amount=" + transAmount
                + " account=" + accountNr
                + " status=" + status;
    }
}
