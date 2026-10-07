package io.smartmoney.api;

import io.smartmoney.api.accountlink.PlatformServicesClient;
import io.smartmoney.api.bankintegration.DemoTransactionService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A customer who links the reserved demonstration account receives the demo's
 * money in and money out on their dashboard, for Equity like the other banks.
 * A real account never receives a simulated movement.
 */
@SpringBootTest
class DemoAccountDeliveryTests {

    @Autowired private WebApplicationContext context;
    @MockitoBean private PlatformServicesClient platform;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private void link(String bankId, String accountNumber, String userId) throws Exception {
        mvc.perform(post("/api/v1/admin/account-links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankId\":\"" + bankId + "\",\"accountNumber\":\"" + accountNumber
                                + "\",\"userId\":\"" + userId + "\",\"accountName\":\"Demo\"}"))
                .andExpect(status().isCreated());
    }

    private void demo(String bankId, String direction, String amount) throws Exception {
        mvc.perform(post("/api/v1/admin/demo/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankId\":\"" + bankId + "\",\"direction\":\"" + direction
                                + "\",\"amount\":" + amount + "}"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void anEquityDemoCreditAndDebitReachTheLinkedCustomer() throws Exception {
        String userId = UUID.randomUUID().toString();
        when(platform.ensureAccount(eq(userId), eq("Equity"), eq(DemoTransactionService.DEMO_ACCOUNT_NUMBER), any()))
                .thenReturn("equity-demo-account");
        when(platform.recordTransaction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PlatformServicesClient.Recorded.CREATED);
        link("equity", DemoTransactionService.DEMO_ACCOUNT_NUMBER, userId);

        demo("equity", "Credit", "5000.00");
        demo("equity", "Debit", "1200.00");

        verify(platform, timeout(5000)).recordTransaction(eq("equity-demo-account"),
                argThat(amount -> amount.compareTo(new BigDecimal("5000.00")) == 0), eq("KES"), eq("CREDIT"),
                any(), any(), any(), any());
        verify(platform, timeout(5000)).recordTransaction(eq("equity-demo-account"),
                argThat(amount -> amount.compareTo(new BigDecimal("1200.00")) == 0), eq("KES"), eq("DEBIT"),
                any(), any(), any(), any());
        verify(platform, timeout(5000)).adjustBalance(eq("equity-demo-account"),
                argThat(delta -> delta.compareTo(new BigDecimal("-1200.00")) == 0));
    }

    @Test
    void aRealAccountNeverReceivesASimulatedMovement() throws Exception {
        String userId = UUID.randomUUID().toString();
        String realAccountNumber = "0170298888888";
        when(platform.ensureAccount(any(), any(), eq(realAccountNumber), any())).thenReturn("real-account");
        link("equity", realAccountNumber, userId);

        mvc.perform(post("/api/v1/admin/demo/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankId\":\"equity\",\"direction\":\"Credit\",\"amount\":700,"
                                + "\"accountNumber\":\"" + realAccountNumber + "\"}"))
                .andExpect(status().is2xxSuccessful());

        verify(platform, after(1500).never()).recordTransaction(eq("real-account"),
                any(), any(), any(), any(), any(), any(), any());
    }
}
