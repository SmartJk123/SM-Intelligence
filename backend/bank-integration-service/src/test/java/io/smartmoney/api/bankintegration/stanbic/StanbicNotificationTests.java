package io.smartmoney.api.bankintegration.stanbic;

import io.smartmoney.api.bankintegration.BankConnectionSettings;
import io.smartmoney.api.bankintegration.BankConnectionTest;
import io.smartmoney.api.bankintegration.NormalizedTransactionEntity;
import io.smartmoney.api.bankintegration.NormalizedTransactionRepository;
import io.smartmoney.api.bankintegration.StepKey;
import io.smartmoney.api.bankintegration.StepStatus;
import io.smartmoney.api.bankintegration.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "smartmoney.stanbic.signature-verification=true",
        "smartmoney.stanbic.signature-header=X-Stanbic-Signature",
        "smartmoney.stanbic.signature-secret=test-secret-key-stanbic-987",
        "smartmoney.stanbic.client-key=mock-stanbic-client-key",
        "smartmoney.stanbic.client-secret=mock-stanbic-client-secret"
})
class StanbicNotificationTests {

    private static final String SIGNATURE_SECRET = "test-secret-key-stanbic-987";
    private static final String SIGNATURE_HEADER = "X-Stanbic-Signature";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private WebhookEventRepository webhookEvents;

    @Autowired
    private NormalizedTransactionRepository transactions;

    @Autowired
    private StanbicConnector connector;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("GET /api/v1/webhooks/stanbic returns readable endpoint verification message")
    void probeReturnsReadableMessage() throws Exception {
        mvc.perform(get("/api/v1/webhooks/stanbic"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("Stanbic Bank Kenya")))
                .andExpect(content().string(containsString("POST payment notifications to this URL")));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/stanbic with valid HMAC-SHA256 signature accepts notification and creates transaction")
    void receivesNotificationWithValidSignature() throws Exception {
        String reference = "STANBIC-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String jsonPayload = """
                {
                    "transactionReference": "%s",
                    "amount": 75000.50,
                    "currency": "KES",
                    "direction": "CREDIT",
                    "narration": "Customer Salary Deposit",
                    "bookingDate": "2026-09-23T10:15:30Z"
                }
                """.formatted(reference);

        String signature = computeHmacSha256(SIGNATURE_SECRET, jsonPayload);

        mvc.perform(post("/api/v1/webhooks/stanbic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, signature)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"))
                .andExpect(jsonPath("$.signatureVerified").value(true));

        NormalizedTransactionEntity tx = awaitTransaction(reference);
        assertThat(tx).isNotNull();
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("75000.50"));
        assertThat(tx.getCurrency()).isEqualTo("KES");
        assertThat(tx.getDirection()).isEqualTo("CREDIT");
        assertThat(tx.getNarration()).isEqualTo("Customer Salary Deposit");
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/stanbic with invalid signature returns 401 Unauthorized")
    void rejectsNotificationWhenSignatureIsInvalid() throws Exception {
        String reference = "STANBIC-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String jsonPayload = """
                {
                    "transactionReference": "%s",
                    "amount": 1000.00,
                    "currency": "KES"
                }
                """.formatted(reference);

        mvc.perform(post("/api/v1/webhooks/stanbic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, "invalid_hmac_hex_value")
                        .content(jsonPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("rejected"));

        Optional<NormalizedTransactionEntity> tx = transactions.findFirstByBankIdAndExternalEventId("stanbic", reference);
        assertThat(tx).isEmpty();
    }

    @Test
    @DisplayName("StanbicConnector connection test probes configured signature and steps")
    void connectorConnectionTestProducesValidSteps() {
        BankConnectionSettings settings = BankConnectionSettings.defaults("stanbic");

        BankConnectionTest result = connector.testConnection(settings);
        assertThat(result).isNotNull();
        assertThat(result.steps()).isNotEmpty();

        // Signature step should be OK since signature verification and secret are set
        boolean hasSignatureOk = result.steps().stream()
                .anyMatch(s -> s.key() == StepKey.SIGNATURE_VERIFICATION && s.status() == StepStatus.OK);
        assertThat(hasSignatureOk).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/stanbic without signature header returns 401 Unauthorized")
    void rejectsNotificationWhenSignatureHeaderIsMissing() throws Exception {
        String reference = "STANBIC-NO-SIG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String jsonPayload = """
                {
                    "transactionReference": "%s",
                    "amount": 5000.00,
                    "currency": "KES"
                }
                """.formatted(reference);

        mvc.perform(post("/api/v1/webhooks/stanbic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("rejected"));

        Optional<NormalizedTransactionEntity> tx = transactions.findFirstByBankIdAndExternalEventId("stanbic", reference);
        assertThat(tx).isEmpty();
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/stanbic with duplicate reference returns 200 and deduplicates")
    void duplicateNotificationIgnoredAndStoredOnce() throws Exception {
        String reference = "STANBIC-DUP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String jsonPayload = """
                {
                    "transactionReference": "%s",
                    "amount": 12000.00,
                    "currency": "KES",
                    "direction": "CREDIT",
                    "narration": "Invoice Payment"
                }
                """.formatted(reference);
        String signature = computeHmacSha256(SIGNATURE_SECRET, jsonPayload);

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/webhooks/stanbic")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(SIGNATURE_HEADER, signature)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("received"));
        }

        NormalizedTransactionEntity tx = awaitTransaction(reference);
        assertThat(tx).isNotNull();

        long storedEvents = webhookEvents.findAll().stream()
                .filter(e -> reference.equals(e.getExternalEventId()))
                .count();
        assertThat(storedEvents).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/stanbic with negative amount normalizes to DEBIT direction and positive magnitude")
    void receivesDebitNotificationWithNegativeAmount() throws Exception {
        String reference = "STANBIC-DEBIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String jsonPayload = """
                {
                    "transactionReference": "%s",
                    "amount": -3500.00,
                    "currency": "KES",
                    "narration": "ATM Withdrawal"
                }
                """.formatted(reference);
        String signature = computeHmacSha256(SIGNATURE_SECRET, jsonPayload);

        mvc.perform(post("/api/v1/webhooks/stanbic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, signature)
                        .content(jsonPayload))
                .andExpect(status().isOk());

        NormalizedTransactionEntity tx = awaitTransaction(reference);
        assertThat(tx).isNotNull();
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("3500.00"));
        assertThat(tx.getDirection()).isEqualTo("DEBIT");
    }

    @Test
    @DisplayName("GET /api/v1/admin/bank-integrations/stanbic returns Stanbic health info")
    void adminHealthReturnsStanbicInfo() throws Exception {
        mvc.perform(get("/api/v1/admin/bank-integrations/stanbic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankId").value("stanbic"))
                .andExpect(jsonPath("$.environment").value("Sandbox"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/bank-integrations/stanbic/credentials returns credential status without leaking secrets")
    void adminCredentialsReturnsMaskedStatus() throws Exception {
        mvc.perform(get("/api/v1/admin/bank-integrations/stanbic/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankId").value("stanbic"))
                .andExpect(jsonPath("$.clientKeyConfigured").value(true))
                .andExpect(jsonPath("$.clientSecretConfigured").value(true))
                .andExpect(jsonPath("$.registrationReady").value(true));
    }

    @Test
    @DisplayName("GET and PUT /api/v1/admin/bank-integrations/stanbic/settings updates settings")
    void adminSettingsGetAndPut() throws Exception {
        mvc.perform(get("/api/v1/admin/bank-integrations/stanbic/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankId").value("stanbic"));

        String updatePayload = """
                {
                    "bankId": "stanbic",
                    "environment": "SANDBOX",
                    "signatureVerification": true,
                    "autoReconcile": false
                }
                """;

        mvc.perform(put("/api/v1/admin/bank-integrations/stanbic/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatureVerification").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/admin/bank-integrations/stanbic/webhook-url returns valid webhook URL")
    void adminWebhookUrlReturnsValidAddress() throws Exception {
        mvc.perform(get("/api/v1/admin/bank-integrations/stanbic/webhook-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankId").value("stanbic"))
                .andExpect(jsonPath("$.webhookUrl").value(containsString("/api/v1/webhooks/stanbic")));
    }

    private static String computeHmacSha256(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private NormalizedTransactionEntity awaitTransaction(String reference) throws InterruptedException {
        for (int attempt = 0; attempt < 40; attempt++) {
            var found = transactions.findFirstByBankIdAndExternalEventId("stanbic", reference);
            if (found.isPresent()) {
                return found.get();
            }
            Thread.sleep(25);
        }
        return transactions.findFirstByBankIdAndExternalEventId("stanbic", reference).orElse(null);
    }
}
