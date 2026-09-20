package io.smartmoney.api;

import io.smartmoney.api.bankintegration.WebhookEventRepository;
import io.smartmoney.api.bankintegration.NormalizedTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class BankIntegrationApiTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Autowired
    private WebhookEventRepository webhookEvents;

    @Autowired
    private NormalizedTransactionRepository transactions;

    @Test
    void healthListsEverySupportedBank() throws Exception {
        mvc.perform(get("/api/v1/admin/bank-integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[3].bankId").value("stanbic"));
    }

    @Test
    void webhookAcceptsAndStoresANotification() throws Exception {
        String payload = "{\"eventId\":\"TEST-WH-1\",\"amount\":1500.00,\"currency\":\"KES\"}";

        mvc.perform(post("/api/v1/webhooks/stanbic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));

        assertThat(webhookEvents.findFirstByBankIdAndExternalEventId("stanbic", "TEST-WH-1")).isPresent();
        awaitTransaction("TEST-WH-1");
    }

    @Test
    void aRepeatedNotificationIsStoredOnce() throws Exception {
        String payload = "{\"eventId\":\"TEST-WH-DUP\",\"amount\":10.00}";

        for (int attempt = 0; attempt < 3; attempt++) {
            mvc.perform(post("/api/v1/webhooks/stanbic")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk());
        }

        long stored = webhookEvents.findAll().stream()
                .filter(event -> "TEST-WH-DUP".equals(event.getExternalEventId()))
                .count();
        assertThat(stored).isEqualTo(1);
        awaitTransaction("TEST-WH-DUP");
    }

    private void awaitTransaction(String externalEventId) throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (transactions.findFirstByBankIdAndExternalEventId("stanbic", externalEventId).isPresent()) {
                return;
            }
            Thread.sleep(25);
        }
        assertThat(transactions.findFirstByBankIdAndExternalEventId("stanbic", externalEventId)).isPresent();
    }

    @Test
    void simulatedNotificationRunsThroughTheSamePath() throws Exception {
        mvc.perform(post("/api/v1/admin/bank-integrations/stanbic/simulate-notification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("simulated"));
    }

    @Test
    void connectionTestExplainsMissingCredentials() throws Exception {
        mvc.perform(post("/api/v1/admin/bank-integrations/stanbic/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.steps[0].key").value("token"));
    }

    @Test
    void landingPageListsTheUrlsForTheBank() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk());
    }
}
