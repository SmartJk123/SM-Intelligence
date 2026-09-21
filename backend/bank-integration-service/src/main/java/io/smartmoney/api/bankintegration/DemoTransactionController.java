package io.smartmoney.api.bankintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.bankintegration.DemoTransactionService.DemoMovement;
import io.smartmoney.api.bankintegration.DemoTransactionService.DemoSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/**
 * The demonstration account.
 *
 * A sandbox cannot show a real account moving, so this account exists to show
 * money arriving and leaving during a demonstration. It is the only place in
 * the admin surface where an amount appears, and every movement on it is
 * labelled as simulated. Nothing here is counted as a bank delivery.
 */
@RestController
@RequestMapping("/api/v1/admin/demo")
public class DemoTransactionController {

    private final DemoTransactionService demo;
    private final ObjectMapper mapper;

    public DemoTransactionController(DemoTransactionService demo, ObjectMapper mapper) {
        this.demo = demo;
        this.mapper = mapper;
    }

    @GetMapping("/account")
    public DemoAccount account() {
        return new DemoAccount(
                DemoTransactionService.DEMO_ACCOUNT_NUMBER,
                DemoTransactionService.DEMO_ACCOUNT_NAME,
                true);
    }

    @GetMapping("/transactions")
    public List<DemoMovement> transactions() {
        return demo.recent();
    }

    @GetMapping("/summary")
    public DemoSummary summary() {
        return demo.summary();
    }

    /**
     * Creates one movement. The body is optional, so a bare POST records a
     * credit of the default amount.
     *
     * {"bankId":"kcb","direction":"Debit","amount":2500.00,"narration":"Supplier payment"}
     */
    @PostMapping(value = "/transactions",
            consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DemoMovement record(@RequestBody(required = false) String rawBody) {
        JsonNode body = read(rawBody);
        return demo.record(
                text(body, "bankId"),
                text(body, "direction"),
                decimal(body, "amount"),
                text(body, "narration"),
                text(body, "accountNumber")).movement();
    }

    private JsonNode read(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(rawBody);
        } catch (Exception error) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The request body has to be JSON");
        }
    }

    private static String text(JsonNode body, String field) {
        if (body == null) { return null; }
        JsonNode value = body.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static BigDecimal decimal(JsonNode body, String field) {
        if (body == null) { return null; }
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) { return null; }
        if (value.isNumber()) { return value.decimalValue(); }
        try {
            return new BigDecimal(value.asText().trim());
        } catch (NumberFormatException error) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Amount has to be a number");
        }
    }

    /** Who the account is, so the interface does not have to hardcode it. */
    public record DemoAccount(String accountNumber, String accountName, boolean simulated) {
    }
}
