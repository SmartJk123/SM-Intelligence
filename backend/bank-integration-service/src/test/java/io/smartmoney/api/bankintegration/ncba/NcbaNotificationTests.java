package io.smartmoney.api.bankintegration.ncba;

import io.smartmoney.api.bankintegration.NormalizedTransactionEntity;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The NCBA endpoint, its HashVal check, the duplicate reply and the way an XML
 * notification reaches the transaction pipeline.
 *
 * The expected hash below is what the documented C# sample produces for the
 * same secret key and field values. It was computed independently, so a change
 * to the algorithm fails here rather than silently in production.
 */
@SpringBootTest(properties = {
        "smartmoney.ncba.secret-key=z#YUNq5b",
        "smartmoney.ncba.username=smartmoney-test",
        "smartmoney.ncba.password=TestPassword123456",
        "smartmoney.ncba.account-number=1234567890",
        "smartmoney.ncba.signature-verification=true"
})
class NcbaNotificationTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static final String SECRET_KEY = "z#YUNq5b";
    private static final String USERNAME = "smartmoney-test";
    private static final String PASSWORD = "TestPassword123456";

    /** secret key z#YUNq5b, then the nine values from the C# sample. */
    private static final String DOCUMENTED_HASH =
            "ZTczOGJhYWMzMGVlNDM1Yjk0MGQyMmIzZDIwNzcyOWMxY2NjNWE3Njc1Zjg3NTAxZmQ1ZGE5ODBmOThlY2EzOA==";


    @Autowired
    private WebhookEventRepository webhookEvents;

    @Autowired
    private NormalizedTransactionRepository transactions;

    @Test
    void hashMatchesTheDocumentedExample() {
        NcbaNotification notification =
                notification("FT22124NTMLC", "30700.00", DOCUMENTED_HASH, USERNAME, PASSWORD);

        assertThat(NcbaSignatureVerifier.hash(SECRET_KEY, notification))
                .isEqualTo(DOCUMENTED_HASH);
    }

    @Test
    void theProbeNamesTheBank() throws Exception {
        mvc.perform(get("/api/v1/webhooks/ncba"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NCBA Bank Kenya")));
    }

    @Test
    void aSignedNotificationIsAcceptedAndStoredAsACredit() throws Exception {
        String reference = unique();

        mvc.perform(post("/api/v1/webhooks/ncba")
                        .contentType(MediaType.TEXT_XML)
                        .content(signedBody(reference, "15000.00")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<Result>OK</Result>")));

        assertThat(webhookEvents.findFirstByBankIdAndExternalEventId("ncba", reference)).isPresent();
        NormalizedTransactionEntity stored = awaitTransaction(reference);
        assertThat(stored.getDirection()).isEqualTo("Credit");
        assertThat(stored.getAmount()).isEqualByComparingTo("15000.00");
    }

    /** NCBA sends a debit as a negative amount, so the sign carries the direction. */
    @Test
    void aNegativeAmountIsStoredAsADebitOfTheSameMagnitude() throws Exception {
        String reference = unique();

        mvc.perform(post("/api/v1/webhooks/ncba")
                        .contentType(MediaType.TEXT_XML)
                        .content(signedBody(reference, "-4200.50")))
                .andExpect(status().isOk());

        NormalizedTransactionEntity stored = awaitTransaction(reference);
        assertThat(stored.getDirection()).isEqualTo("Debit");
        assertThat(stored.getAmount()).isEqualByComparingTo("4200.50");
    }

    @Test
    void aRepeatedNotificationIsAnsweredAsADuplicate() throws Exception {
        String reference = unique();
        String body = signedBody(reference, "2500.00");

        mvc.perform(post("/api/v1/webhooks/ncba")
                        .contentType(MediaType.TEXT_XML)
                        .content(body))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/webhooks/ncba")
                        .contentType(MediaType.TEXT_XML)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("OK: Duplicate Notification")));

        assertThat(webhookEvents.findAll().stream()
                .filter(event -> reference.equals(event.getExternalEventId()))
                .count()).isEqualTo(1);
    }

    @Test
    void aNotificationWithTheWrongHashIsRefused() throws Exception {
        String reference = unique();

        mvc.perform(post("/api/v1/webhooks/ncba")
                        .contentType(MediaType.TEXT_XML)
                        .content(body(notification(reference, "1500.00",
                                "not-the-right-hash", USERNAME, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<Result>FAIL:")));

        assertThat(webhookEvents.findFirstByBankIdAndExternalEventId("ncba", reference)).isEmpty();
    }

    @Test
    void aNotificationWithTheWrongPasswordIsRefused() throws Exception {
        String reference = unique();

        mvc.perform(post("/api/v1/webhooks/ncba")
                        .contentType(MediaType.TEXT_XML)
                        .content(signedBody(reference, "1500.00", USERNAME, "the-wrong-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<Result>FAIL:")));
    }

    @Test
    void aBodyThatIsNotXmlIsRefusedRatherThanStored() throws Exception {
        String reference = unique();

        mvc.perform(post("/api/v1/webhooks/ncba")
                        .contentType(MediaType.TEXT_XML)
                        .content("this is not xml " + reference))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<Result>FAIL:")));

        assertThat(webhookEvents.findFirstByBankIdAndExternalEventId("ncba", reference)).isEmpty();
    }

    /**
     * The toggle in the admin interface has to change what the endpoint does.
     * Until this test existed the switch was stored and reported while the
     * verifier read the environment variable instead, so an operator turning
     * verification off saw no change at all.
     */
    @Test
    void turningVerificationOffInTheAdminInterfaceStopsTheHashCheck() throws Exception {
        try {
            mvc.perform(put("/api/v1/admin/bank-integrations/ncba")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"signatureVerification\":false}"))
                    .andExpect(status().isOk());

            String reference = unique();
            mvc.perform(post("/api/v1/webhooks/ncba")
                            .contentType(MediaType.TEXT_XML)
                            .content(body(notification(reference, "1500.00",
                                    "not-the-right-hash", USERNAME, PASSWORD))))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<Result>OK</Result>")));

            assertThat(awaitTransaction(reference).getDirection()).isEqualTo("Credit");
        } finally {
            mvc.perform(put("/api/v1/admin/bank-integrations/ncba")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"signatureVerification\":true}"))
                    .andExpect(status().isOk());
        }
    }

    /**
     * A request carrying one field must not reset the rest. The booleans are
     * primitives, so before this was fixed an omitted signatureVerification
     * arrived as false and silently switched the check off.
     */
    @Test
    void aPartialSaveLeavesTheOtherSettingsAlone() throws Exception {
        mvc.perform(put("/api/v1/admin/bank-integrations/ncba")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"environment\":\"Production\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/bank-integrations/ncba/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("Production"))
                .andExpect(jsonPath("$.signatureVerification").value(true))
                .andExpect(jsonPath("$.automaticRetry").value(true))
                .andExpect(jsonPath("$.retryAttempts").value(3));

        mvc.perform(put("/api/v1/admin/bank-integrations/ncba")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"environment\":\"Sandbox\"}"))
                .andExpect(status().isOk());
    }

    private static String unique() {
        return "NCBA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** A notification carrying the values of the documented sample. */
    private static NcbaNotification notification(
            String reference, String amount, String hashVal, String user, String password) {
        return new NcbaNotification(
                user, password, hashVal, "220", reference, "2205041019", amount,
                "6956820019", "RFBContributio", "", "SAFARICOM SACCO LTD", "SUCCESS",
                "ID-12345", Map.of());
    }

    private static String signedBody(String reference, String amount) {
        return signedBody(reference, amount, USERNAME, PASSWORD);
    }

    private static String signedBody(
            String reference, String amount, String user, String password) {
        NcbaNotification unsigned = notification(reference, amount, "", user, password);
        String hash = NcbaSignatureVerifier.hash(SECRET_KEY, unsigned);
        return body(notification(reference, amount, hash, user, password));
    }

    private static String body(NcbaNotification n) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <NCBAPaymentNotificationRequest>
                      <User>%s</User>
                      <Password>%s</Password>
                      <HashVal>%s</HashVal>
                      <TransType>%s</TransType>
                      <TransID>%s</TransID>
                      <TransTime>%s</TransTime>
                      <TransAmount>%s</TransAmount>
                      <AccountNr>%s</AccountNr>
                      <Narrative>%s</Narrative>
                      <PhoneNr>%s</PhoneNr>
                      <CustomerName>%s</CustomerName>
                      <Status>%s</Status>
                      <FtCrNarration>%s</FtCrNarration>
                    </NCBAPaymentNotificationRequest>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(n.user(), n.password(), n.hashVal(), n.transType(), n.transId(),
                        n.transTime(), n.transAmount(), n.accountNr(), n.narrative(),
                        n.phoneNr(), n.customerName(), n.status(), n.ftCrNarration());
    }

    /** Processing runs after the acknowledgement, so give it a moment. */
    private NormalizedTransactionEntity awaitTransaction(String reference) throws InterruptedException {
        for (int attempt = 0; attempt < 40; attempt++) {
            var found = transactions.findFirstByBankIdAndExternalEventId("ncba", reference);
            if (found.isPresent()) {
                return found.get();
            }
            Thread.sleep(25);
        }
        assertThat(transactions.findFirstByBankIdAndExternalEventId("ncba", reference)).isPresent();
        return null;
    }
}
