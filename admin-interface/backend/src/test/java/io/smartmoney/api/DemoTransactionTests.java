package io.smartmoney.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.bankintegration.DemoTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The demonstration account. Every assertion here is about money moving, and
 * about money moving without ever being mistaken for a bank delivery.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoTransactionTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void aCreditAndADebitBothLandOnTheDemoAccount() throws Exception {
        BigDecimal credit = amountOf(record("""
                {"bankId":"kcb","direction":"Credit","amount":25000.00,"narration":"Demo funds received"}
                """));
        BigDecimal debit = amountOf(record("""
                {"bankId":"kcb","direction":"Debit","amount":4000.00,"narration":"Demo payment out"}
                """));

        assertThat(credit).isEqualByComparingTo("25000.00");
        assertThat(debit).isEqualByComparingTo("4000.00");

        JsonNode movements = awaitMovements(2);
        assertThat(movements.get(0).get("simulated").asBoolean()).isTrue();
        assertThat(movements.get(0).get("accountNumber").asText())
                .isEqualTo(DemoTransactionService.DEMO_ACCOUNT_NUMBER);
    }

    @Test
    void theSummaryAddsTheTwoDirectionsUp() throws Exception {
        record("""
                {"bankId":"stanbic","direction":"Credit","amount":10000.00}
                """);
        record("""
                {"bankId":"stanbic","direction":"Debit","amount":2500.00}
                """);
        awaitMovements(2);

        JsonNode summary = mapper.readTree(
                mvc.perform(get("/api/v1/admin/demo/summary"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        BigDecimal credits = summary.get("creditTotal").decimalValue();
        BigDecimal debits = summary.get("debitTotal").decimalValue();
        assertThat(summary.get("netTotal").decimalValue()).isEqualByComparingTo(credits.subtract(debits));
        assertThat(credits).isGreaterThanOrEqualTo(new BigDecimal("10000.00"));
        assertThat(debits).isGreaterThanOrEqualTo(new BigDecimal("2500.00"));
    }

    @Test
    void aDemonstrationIsNotCountedAsABankDelivery() throws Exception {
        long before = deliveriesFor("kcb");

        record("""
                {"bankId":"kcb","direction":"Credit","amount":1234.00}
                """);
        awaitMovements(1);

        assertThat(deliveriesFor("kcb")).isEqualTo(before);
    }

    @Test
    void aBarePostRecordsACredit() throws Exception {
        mvc.perform(post("/api/v1/admin/demo/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("Credit"))
                .andExpect(jsonPath("$.simulated").value(true));
    }

    @Test
    void aMovementThatMakesNoSenseIsRefused() throws Exception {
        mvc.perform(post("/api/v1/admin/demo/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-50}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/admin/demo/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"bankId\":\"nowhere\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/admin/demo/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"direction\":\"sideways\"}"))
                .andExpect(status().isBadRequest());
    }

    private String record(String body) throws Exception {
        return mvc.perform(post("/api/v1/admin/demo/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private BigDecimal amountOf(String response) throws Exception {
        return mapper.readTree(response).get("amount").decimalValue();
    }

    private long deliveriesFor(String bankId) throws Exception {
        JsonNode health = mapper.readTree(
                mvc.perform(get("/api/v1/admin/bank-integrations/" + bankId))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        return health.get("notificationsTotal").asLong();
    }

    /** Normalisation runs after the response, so wait for it rather than guess. */
    private JsonNode awaitMovements(int expected) throws Exception {
        JsonNode movements = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            movements = mapper.readTree(
                    mvc.perform(get("/api/v1/admin/demo/transactions"))
                            .andExpect(status().isOk())
                            .andReturn().getResponse().getContentAsString());
            if (movements.size() >= expected) {
                return movements;
            }
            Thread.sleep(50);
        }
        assertThat(movements).isNotNull();
        assertThat(movements.size()).isGreaterThanOrEqualTo(expected);
        return movements;
    }
}
