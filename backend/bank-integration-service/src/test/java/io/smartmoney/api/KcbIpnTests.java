package io.smartmoney.api;

import io.smartmoney.api.bankintegration.NormalizedTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KCB's instant payment notification, in the exact shape of the IPN
 * specification, signed with a test key standing in for KCB's.
 */
@SpringBootTest
class KcbIpnTests {

    private static final KeyPair KCB_KEYS = keys();

    private static KeyPair keys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @DynamicPropertySource
    static void kcbPublicKey(DynamicPropertyRegistry registry) {
        registry.add("smartmoney.kcb.public-key",
                () -> Base64.getEncoder().encodeToString(KCB_KEYS.getPublic().getEncoded()));
        registry.add("smartmoney.kcb.signature-verification", () -> "true");
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Autowired
    private NormalizedTransactionRepository transactions;

    private static String sign(String body) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(KCB_KEYS.getPrivate());
        signature.update(body.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private static String notification(String reference) {
        return """
                {
                 "transactionReference": "%s",
                 "requestId": "c7d702cb-6b5f-4fa6-8b57-436d0f789017",
                 "channelCode": "202",
                 "timestamp": "20261005103005",
                 "transactionAmount": "100.00",
                 "currency": "KES",
                 "customerReference": "INV-0001",
                 "customerName": "John Doe",
                 "customerMobileNumber": "254711111111",
                 "balance": "",
                 "narration": "Payment for goods",
                 "creditAccountIdentifier": "1302360167",
                 "organizationShortCode": "777777",
                 "tillNumber": "150150"
                }
                """.formatted(reference);
    }

    @Test
    void acceptsASignedNotificationAndRecordsItAsACredit() throws Exception {
        String body = notification("FT-KCB-IPN-1");
        mvc.perform(post("/api/v1/webhooks/kcb").contentType(MediaType.APPLICATION_JSON)
                        .header("Signature", sign(body)).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionID").value("FT-KCB-IPN-1"))
                .andExpect(jsonPath("$.statusCode").value("0"))
                .andExpect(jsonPath("$.statusMessage").value("Notification received successfully"));

        // Processing runs in the background after the acknowledgement.
        var stored = transactions.findFirstByBankIdAndExternalEventId("kcb", "FT-KCB-IPN-1");
        for (int attempt = 0; stored.isEmpty() && attempt < 50; attempt++) {
            Thread.sleep(100);
            stored = transactions.findFirstByBankIdAndExternalEventId("kcb", "FT-KCB-IPN-1");
        }
        var movement = stored.orElseThrow();
        assertThat(movement.getDirection()).isEqualTo("CREDIT");
        assertThat(movement.getAmount()).isEqualByComparingTo("100.00");
        assertThat(movement.getAccountNumber()).isEqualTo("1302360167");
        assertThat(movement.getCounterpartyName()).isEqualTo("John Doe");
        assertThat(movement.getCounterpartyPhone()).isEqualTo("254711111111");
        assertThat(movement.getBookingDate()).isEqualTo(Instant.parse("2026-10-05T07:30:05Z"));
    }

    @Test
    void refusesANotificationWhoseSignatureDoesNotMatch() throws Exception {
        String body = notification("FT-KCB-IPN-2");
        mvc.perform(post("/api/v1/webhooks/kcb").contentType(MediaType.APPLICATION_JSON)
                        .header("Signature", sign(body.replace("100.00", "900.00"))).content(body))
                .andExpect(status().isUnauthorized());
        assertThat(transactions.findFirstByBankIdAndExternalEventId("kcb", "FT-KCB-IPN-2")).isEmpty();
    }
}
