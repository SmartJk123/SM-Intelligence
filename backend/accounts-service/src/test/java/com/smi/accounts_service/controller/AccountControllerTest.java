package com.smi.accounts_service.controller;

import com.smi.accounts_service.dto.AccountResponse;
import com.smi.accounts_service.dto.CreateAccountRequest;
import com.smi.accounts_service.dto.UpdateBalanceRequest;
import com.smi.accounts_service.exception.AccountNotFoundException;
import com.smi.accounts_service.exception.DuplicateAccountException;
import com.smi.accounts_service.exception.GlobalExceptionHandler;
import com.smi.accounts_service.service.AccountService;
import com.smi.accounts_service.service.AccountIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccountService accountService;
    @Mock private AccountIdentity identity;

    @InjectMocks
    private AccountController accountController;

    private UUID userId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(accountController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        org.mockito.Mockito.lenient().when(identity.owner(any())).thenReturn(userId);
    }

    private AccountResponse createSampleResponse() {
        AccountResponse response = new AccountResponse();
        response.setId(accountId);
        response.setUserId(userId);
        response.setProviderAccountId("ACC-123456");
        response.setAccountName("Main Savings");
        response.setInstitution("Equity Bank");
        response.setAccountType("DEPOSIT");
        response.setMaskedIdentifier("****5678");
        response.setCurrency("KES");
        response.setLedgerBalance(new BigDecimal("150000.0000"));
        response.setAvailableBalance(new BigDecimal("150000.0000"));
        response.setAccountStatus("ACTIVE");
        response.setConnectionStatus("CONNECTED");
        response.setDataSource("MANUAL");
        response.setCreatedAt(OffsetDateTime.now());
        response.setUpdatedAt(OffsetDateTime.now());
        return response;
    }

    @Test
    void createAccount_Success() throws Exception {
        String requestJson = """
            {
              "userId": "%s",
              "providerAccountId": "ACC-123456",
              "accountName": "Main Savings",
              "institution": "Equity Bank",
              "accountType": "DEPOSIT",
              "maskedIdentifier": "****5678",
              "currency": "KES",
              "initialBalance": 150000.0,
              "creditLimit": 0.0,
              "dataSource": "MANUAL"
            }
            """.formatted(userId);

        AccountResponse response = createSampleResponse();
        when(accountService.createAccount(any(CreateAccountRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(accountId.toString()))
            .andExpect(jsonPath("$.accountName").value("Main Savings"))
            .andExpect(jsonPath("$.institution").value("Equity Bank"))
            .andExpect(jsonPath("$.currency").value("KES"))
            .andExpect(jsonPath("$.availableBalance").value(150000.0));
    }

    @Test
    void createAccount_ValidationFailure_MissingRequiredFields() throws Exception {
        String invalidJson = "{}";

        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Validation Failed"))
            .andExpect(jsonPath("$.details.userId").exists())
            .andExpect(jsonPath("$.details.accountName").exists());
    }

    @Test
    void createAccount_Duplicate_Conflict() throws Exception {
        String requestJson = """
            {
              "userId": "%s",
              "providerAccountId": "ACC-123456",
              "accountName": "Main Savings",
              "institution": "Equity Bank",
              "accountType": "DEPOSIT",
              "maskedIdentifier": "****5678",
              "currency": "KES"
            }
            """.formatted(userId);

        when(accountService.createAccount(any(CreateAccountRequest.class)))
            .thenThrow(new DuplicateAccountException("Account already exists"));

        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.message").value("Account already exists"));
    }

    @Test
    void getAccounts_Success() throws Exception {
        AccountResponse response = createSampleResponse();
        when(accountService.getAccountsByUserId(eq(userId), any(), any(), any(), any())).thenReturn(List.of(response));

        mockMvc.perform(get("/api/accounts")
                .param("userId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(accountId.toString()))
            .andExpect(jsonPath("$[0].accountName").value("Main Savings"));
    }

    @Test
    void getAccountById_Success() throws Exception {
        AccountResponse response = createSampleResponse();
        when(accountService.getAccountById(eq(accountId), any())).thenReturn(response);

        mockMvc.perform(get("/api/accounts/{id}", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(accountId.toString()))
            .andExpect(jsonPath("$.accountName").value("Main Savings"));
    }

    @Test
    void getAccountById_NotFound() throws Exception {
        when(accountService.getAccountById(eq(accountId), any()))
            .thenThrow(new AccountNotFoundException("Account not found with ID: " + accountId));

        mockMvc.perform(get("/api/accounts/{id}", accountId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void updateBalance_Success() throws Exception {
        String updateJson = """
            {
              "availableBalance": 200000.0,
              "ledgerBalance": 200000.0,
              "creditOutstanding": 0.0
            }
            """;

        AccountResponse response = createSampleResponse();
        response.setAvailableBalance(new BigDecimal("200000.0000"));
        response.setLedgerBalance(new BigDecimal("200000.0000"));

        when(accountService.updateBalance(eq(accountId), any(UpdateBalanceRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/accounts/{id}/balance", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableBalance").value(200000.0));
    }

    @Test
    void deleteAccount_Success() throws Exception {
        AccountResponse response = createSampleResponse();

        when(accountService.deleteAccount(eq(accountId))).thenReturn(response);

        mockMvc.perform(delete("/api/accounts/{id}", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(accountId.toString()));
    }

    @Test
    void closeAccount_Success() throws Exception {
        AccountResponse response = createSampleResponse();
        response.setAccountStatus("CLOSED");
        response.setConnectionStatus("DISCONNECTED");

        when(accountService.closeAccount(eq(accountId))).thenReturn(response);

        mockMvc.perform(delete("/api/accounts/{id}", accountId).param("permanent", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountStatus").value("CLOSED"))
            .andExpect(jsonPath("$.connectionStatus").value("DISCONNECTED"));
    }

    @Test
    void getAccountSummary_Success() throws Exception {
        com.smi.accounts_service.dto.AccountSummaryResponse summary = new com.smi.accounts_service.dto.AccountSummaryResponse(
                new BigDecimal("150000.00"),
                new BigDecimal("200000.00"),
                new BigDecimal("50000.00"),
                new BigDecimal("100000.00"),
                new BigDecimal("50000.00"),
                2,
                2,
                "KES",
                java.util.Map.of("Equity", new BigDecimal("200000.00")),
                java.util.Map.of("DEPOSIT", new BigDecimal("200000.00"), "CREDIT", new BigDecimal("50000.00"))
        );

        when(accountService.getAccountSummary(eq(userId))).thenReturn(summary);

        mockMvc.perform(get("/api/accounts/summary")
                .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.netWorth").value(150000.0))
            .andExpect(jsonPath("$.totalDeposits").value(200000.0))
            .andExpect(jsonPath("$.totalCreditDebt").value(50000.0))
            .andExpect(jsonPath("$.totalAccounts").value(2));
    }

    @Test
    void renameAccount_Success() throws Exception {
        AccountResponse response = createSampleResponse();
        response.setAccountName("My Emergency Fund");

        when(accountService.renameAccount(eq(accountId), eq(userId), eq("My Emergency Fund"))).thenReturn(response);

        mockMvc.perform(patch("/api/accounts/{id}/rename", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountName\": \"My Emergency Fund\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountName").value("My Emergency Fund"));
    }

    @Test
    void syncAccount_Success() throws Exception {
        AccountResponse response = createSampleResponse();
        response.setConnectionStatus("CONNECTED");

        when(accountService.syncAccount(eq(accountId), eq(userId))).thenReturn(response);

        mockMvc.perform(post("/api/accounts/{id}/sync", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connectionStatus").value("CONNECTED"));
    }

    @Test
    void reopenAccount_Success() throws Exception {
        AccountResponse response = createSampleResponse();
        response.setAccountStatus("ACTIVE");

        when(accountService.reopenAccount(eq(accountId), eq(userId))).thenReturn(response);

        mockMvc.perform(post("/api/accounts/{id}/reopen", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
    }
}
