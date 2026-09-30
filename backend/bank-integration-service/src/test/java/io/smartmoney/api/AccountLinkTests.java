package io.smartmoney.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.accountlink.PlatformServicesClient;
import io.smartmoney.api.bankintegration.NormalizedTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A bank account linked to a customer: movements that arrived before the link
 * are delivered when it is made, later ones as they arrive, and an outage is
 * retried. accounts-service and transactions-service are replaced by a mock.
 */
@SpringBootTest
class AccountLinkTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Autowired
    private NormalizedTransactionRepository movements;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private PlatformServicesClient platform;

    private void notify(String eventId, String accountNumber, String amount) throws Exception {
        mvc.perform(post("/api/v1/webhooks/stanbic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + eventId + "\",\"amount\":" + amount
                                + ",\"currency\":\"KES\",\"creditDebitIndicator\":\"Credit\",\"accountNumber\":\""
                                + accountNumber + "\",\"transactionReference\":\"" + eventId + "\"}"))
                .andExpect(status().isOk());
    }

    private void awaitStored(String eventId) throws InterruptedException {
        for (int i = 0; i < 50 && movements.findFirstByBankIdAndExternalEventId("stanbic", eventId).isEmpty(); i++) {
            Thread.sleep(100);
        }
        assertThat(movements.findFirstByBankIdAndExternalEventId("stanbic", eventId)).isPresent();
    }

    @Test
    void linkedCustomerReceivesEarlierAndLaterMovements() throws Exception {
        String account = "0100" + (System.nanoTime() % 100000000L);
        String userId = UUID.randomUUID().toString();
        when(platform.ensureAccount(eq(userId), eq("Stanbic"), eq(account), any())).thenReturn("acc-1");

        // Arrives before anyone is linked: stored with its account number, not delivered.
        notify("LINK-BEFORE-" + account, account, "2500.00");
        awaitStored("LINK-BEFORE-" + account);
        assertThat(movements.findFirstByBankIdAndExternalEventId("stanbic", "LINK-BEFORE-" + account).get()
                .getAccountNumber()).isEqualTo(account);
        verify(platform, never()).recordTransaction(any(), any(), any(), any(), any(), any(), any());

        // Linking delivers it.
        String body = mvc.perform(post("/api/v1/admin/account-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankId\":\"stanbic\",\"accountNumber\":\"" + account.substring(0, 4) + " "
                                + account.substring(4) + "\",\"userId\":\"" + userId + "\",\"accountName\":\"Rent\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value(account))
                .andExpect(jsonPath("$.accountId").value("acc-1"))
                .andExpect(jsonPath("$.pendingDeliveries").value(0))
                .andReturn().getResponse().getContentAsString();
        long linkId = json.readTree(body).get("id").asLong();
        verify(platform).recordTransaction(eq("acc-1"), eq(new BigDecimal("2500.0000")), eq("KES"), eq("CREDIT"),
                eq("stanbic:LINK-BEFORE-" + account), any(), any(Instant.class));

        // Arrives after: delivered as it is processed.
        notify("LINK-AFTER-" + account, account, "300.00");
        verify(platform, timeout(5000)).recordTransaction(eq("acc-1"), any(), any(), eq("CREDIT"),
                eq("stanbic:LINK-AFTER-" + account), any(), any());

        // The same account cannot be linked twice.
        mvc.perform(post("/api/v1/admin/account-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankId\":\"stanbic\",\"accountNumber\":\"" + account + "\",\"userId\":\""
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/admin/account-links").param("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(linkId));

        // Unlinking closes the dashboard account.
        mvc.perform(delete("/api/v1/admin/account-links/" + linkId)).andExpect(status().isNoContent());
        verify(platform).closeAccount("acc-1");
    }

    @Test
    void anOutageLeavesTheMovementWaitingUntilSynced() throws Exception {
        String account = "0200" + (System.nanoTime() % 100000000L);
        String userId = UUID.randomUUID().toString();
        when(platform.ensureAccount(any(), any(), eq(account), any())).thenReturn("acc-2");
        doThrow(new IllegalStateException("transactions-service is down"))
                .when(platform).recordTransaction(eq("acc-2"), any(), any(), any(), any(), any(), any());

        notify("OUTAGE-" + account, account, "100.00");
        awaitStored("OUTAGE-" + account);

        String body = mvc.perform(post("/api/v1/admin/account-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankId\":\"stanbic\",\"accountNumber\":\"" + account + "\",\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pendingDeliveries").value(1))
                .andExpect(jsonPath("$.lastError").value("transactions-service is down"))
                .andReturn().getResponse().getContentAsString();
        JsonNode link = json.readTree(body);

        reset(platform);
        mvc.perform(post("/api/v1/admin/account-links/" + link.get("id").asLong() + "/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingDeliveries").value(0));
        verify(platform).recordTransaction(eq("acc-2"), any(), any(), eq("CREDIT"), eq("stanbic:OUTAGE-" + account),
                any(), any());
    }

    @Test
    void refusesBadRequests() throws Exception {
        String userId = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/admin/account-links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankId\":\"barclays\",\"accountNumber\":\"12345678\",\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown bank: barclays"));
        mvc.perform(post("/api/v1/admin/account-links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankId\":\"ncba\",\"accountNumber\":\"12\",\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/account-links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankId\":\"ncba\",\"accountNumber\":\"12345678\",\"userId\":\"not-a-user\"}"))
                .andExpect(status().isBadRequest());
    }
}
