package io.smartmoney.api;

import io.smartmoney.api.bankintegration.NormalizedTransactionRepository;
import io.smartmoney.api.bankintegration.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Equity's Jenga Instant Payment Notification, in the exact shape of the
 * published example, protected with Basic Auth.
 */
@SpringBootTest(properties = {
        "smartmoney.equity.ipn-username=jenga-callback",
        "smartmoney.equity.ipn-password=correct-horse-battery",
        "smartmoney.equity.signature-verification=true",
        "smartmoney.equity.account-number=0170299999999"})
class EquityIpnTests {

    @Autowired private WebApplicationContext context;
    @Autowired private NormalizedTransactionRepository transactions;
    @Autowired private WebhookEventRepository events;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String ipn(String reference, String status, String account) {
        return """
                {
                  "callbackType": "IPN",
                  "customer": {"name": "John Doe", "mobileNumber": "254712345678", "reference": "071648816466242"},
                  "transaction": {
                    "date": "2026-10-05 14:15:20", "reference": "%s", "paymentMode": "MPESA", "amount": 150,
                    "currency": "KES", "billNumber": "INVZCF", "servedBy": "EQ", "additionalInfo": "MPESA",
                    "orderAmount": 150, "serviceCharge": 5.25, "orderCurrency": "KES", "status": "%s",
                    "remarks": "00:Approved"
                  },
                  "bank": {"reference": "%s", "transactionType": "C", "account": %s}
                }
                """.formatted(reference, status, reference, account == null ? "null" : "\"" + account + "\"");
    }

    private <T> T await(java.util.function.Supplier<java.util.Optional<T>> lookup) throws InterruptedException {
        var found = lookup.get();
        for (int attempt = 0; found.isEmpty() && attempt < 50; attempt++) {
            Thread.sleep(100);
            found = lookup.get();
        }
        return found.orElseThrow();
    }

    @Test
    void acceptsAnAuthenticatedPaymentAndRecordsItAsACredit() throws Exception {
        mvc.perform(post("/api/v1/webhooks/equity").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basic("jenga-callback", "correct-horse-battery"))
                        .content(ipn("EQ-IPN-1", "SUCCESS", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"))
                .andExpect(jsonPath("$.reference").value("EQ-IPN-1"));

        var movement = await(() -> transactions.findFirstByBankIdAndExternalEventId("equity", "EQ-IPN-1"));
        assertThat(movement.getDirection()).isEqualTo("CREDIT");
        assertThat(movement.getAmount()).isEqualByComparingTo("150");
        // bank.account is null in Jenga's example, so the configured Equity account is used.
        assertThat(movement.getAccountNumber()).isEqualTo("0170299999999");
        assertThat(movement.getCounterpartyName()).isEqualTo("John Doe");
        assertThat(movement.getCounterpartyPhone()).isEqualTo("254712345678");
        assertThat(movement.getBookingDate()).isEqualTo(Instant.parse("2026-10-05T11:15:20Z"));
    }

    @Test
    void refusesMissingOrWrongCredentials() throws Exception {
        mvc.perform(post("/api/v1/webhooks/equity").contentType(MediaType.APPLICATION_JSON)
                        .content(ipn("EQ-IPN-2", "SUCCESS", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"SmartMoney\""));
        mvc.perform(post("/api/v1/webhooks/equity").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basic("jenga-callback", "wrong"))
                        .content(ipn("EQ-IPN-2", "SUCCESS", null)))
                .andExpect(status().isUnauthorized());
        assertThat(events.findFirstByBankIdAndExternalEventId("equity", "EQ-IPN-2")).isEmpty();
    }

    @Test
    void recordsAFailedPaymentButNeverCreditsIt() throws Exception {
        mvc.perform(post("/api/v1/webhooks/equity").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basic("jenga-callback", "correct-horse-battery"))
                        .content(ipn("EQ-IPN-3", "FAILED", "0170211111111")))
                .andExpect(status().isOk());

        var event = await(() -> events.findFirstByBankIdAndExternalEventId("equity", "EQ-IPN-3")
                .filter(e -> !"RECEIVED".equals(e.getProcessingStatus()) && e.getProcessingStatus() != null));
        assertThat(event.getProcessingStatus()).isEqualTo("FAILED");
        assertThat(event.getErrorMessage()).contains("FAILED");
        assertThat(transactions.findFirstByBankIdAndExternalEventId("equity", "EQ-IPN-3")).isEmpty();
    }

    @Test
    void answersABrowserCheck() throws Exception {
        mvc.perform(get("/api/v1/webhooks/equity")).andExpect(status().isOk());
    }
}
