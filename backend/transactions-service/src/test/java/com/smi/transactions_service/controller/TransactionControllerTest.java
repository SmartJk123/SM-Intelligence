package com.smi.transactions_service.controller;

import com.smi.transactions_service.dto.CreateTransactionRequest;
import com.smi.transactions_service.dto.TransactionResponse;
import com.smi.transactions_service.exception.DuplicateTransactionException;
import com.smi.transactions_service.exception.GlobalExceptionHandler;
import com.smi.transactions_service.exception.TransactionNotFoundException;
import com.smi.transactions_service.service.TransactionService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private TransactionController transactionController;

    private UUID accountId;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(transactionController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        accountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
    }

    private TransactionResponse createSampleResponse() {
        TransactionResponse response = new TransactionResponse();
        response.setId(transactionId);
        response.setAccountId(accountId);
        response.setAmount(new BigDecimal("3500.0000"));
        response.setCurrency("KES");
        response.setTransactionType("DEBIT");
        response.setCounterparty("Naivas Supermarket");
        response.setPaymentMethod("M-PESA");
        response.setStatus("POSTED");
        response.setProviderReference("QK789XYZ12");
        response.setDescription("Grocery shopping");
        response.setTransactionDate(OffsetDateTime.now());
        response.setPostingDate(OffsetDateTime.now());
        response.setCreatedAt(OffsetDateTime.now());
        response.setUpdatedAt(OffsetDateTime.now());
        return response;
    }

    @Test
    void recordTransaction_Success() throws Exception {
        String requestJson = """
            {
              "accountId": "%s",
              "amount": 3500.00,
              "currency": "KES",
              "transactionType": "DEBIT",
              "counterparty": "Naivas Supermarket",
              "paymentMethod": "M-PESA",
              "providerReference": "QK789XYZ12",
              "description": "Grocery shopping"
            }
            """.formatted(accountId);

        TransactionResponse response = createSampleResponse();
        when(transactionService.recordTransaction(any(CreateTransactionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(transactionId.toString()))
            .andExpect(jsonPath("$.amount").value(3500.0))
            .andExpect(jsonPath("$.counterparty").value("Naivas Supermarket"))
            .andExpect(jsonPath("$.status").value("POSTED"));
    }

    @Test
    void recordTransaction_ValidationFailure_MissingFields() throws Exception {
        String invalidJson = "{}";

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Validation Failed"))
            .andExpect(jsonPath("$.details.accountId").exists())
            .andExpect(jsonPath("$.details.amount").exists())
            .andExpect(jsonPath("$.details.transactionType").exists());
    }

    @Test
    void recordTransaction_Duplicate_Conflict() throws Exception {
        String requestJson = """
            {
              "accountId": "%s",
              "amount": 3500.00,
              "currency": "KES",
              "transactionType": "DEBIT",
              "providerReference": "QK789XYZ12"
            }
            """.formatted(accountId);

        when(transactionService.recordTransaction(any(CreateTransactionRequest.class)))
            .thenThrow(new DuplicateTransactionException("Transaction already exists"));

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.message").value("Transaction already exists"));
    }

    @Test
    void getTransactionsByAccount_Success() throws Exception {
        TransactionResponse response = createSampleResponse();
        when(transactionService.getTransactionsByAccountId(eq(accountId), any())).thenReturn(List.of(response));

        mockMvc.perform(get("/api/transactions")
                .param("accountId", accountId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(transactionId.toString()))
            .andExpect(jsonPath("$[0].counterparty").value("Naivas Supermarket"));
    }

    @Test
    void getTransactionById_Success() throws Exception {
        TransactionResponse response = createSampleResponse();
        when(transactionService.getTransactionById(eq(transactionId))).thenReturn(response);

        mockMvc.perform(get("/api/transactions/{id}", transactionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(transactionId.toString()))
            .andExpect(jsonPath("$.amount").value(3500.0));
    }

    @Test
    void getTransactionById_NotFound() throws Exception {
        when(transactionService.getTransactionById(eq(transactionId)))
            .thenThrow(new TransactionNotFoundException("Transaction not found with ID: " + transactionId));

        mockMvc.perform(get("/api/transactions/{id}", transactionId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void updateCategory_Success() throws Exception {
        UUID categoryId = UUID.randomUUID();
        String updateJson = """
            {
              "categoryId": "%s"
            }
            """.formatted(categoryId);

        TransactionResponse response = createSampleResponse();
        response.setCategoryId(categoryId);

        when(transactionService.updateCategory(eq(transactionId), eq(categoryId))).thenReturn(response);

        mockMvc.perform(patch("/api/transactions/{id}/category", transactionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryId").value(categoryId.toString()));
    }

    @Test
    void reverseTransaction_Success() throws Exception {
        TransactionResponse response = createSampleResponse();
        response.setTransactionType("CREDIT");
        response.setDescription("Reversal: Duplicate charge");
        response.setRelatedTransactionId(transactionId);

        when(transactionService.reverseTransaction(eq(transactionId), any())).thenReturn(response);

        String reverseJson = """
            {
              "reason": "Duplicate charge"
            }
            """;

        mockMvc.perform(post("/api/transactions/{id}/reverse", transactionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reverseJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionType").value("CREDIT"))
            .andExpect(jsonPath("$.relatedTransactionId").value(transactionId.toString()));
    }
}
