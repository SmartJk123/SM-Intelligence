package com.smi.transactions_service.activity;

import com.smi.transactions_service.domain.Transaction;
import com.smi.transactions_service.invoice.InvoiceIdentity;
import com.smi.transactions_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The activity feed shows only the signed-in user's own accounts' money in and out. */
class ActivityControllerTest {

    TransactionRepository transactions;
    InvoiceIdentity identity;
    AccountDirectory accounts;
    MockMvc mvc;
    UUID account = UUID.randomUUID();

    @BeforeEach
    void setup() {
        transactions = mock(TransactionRepository.class);
        identity = mock(InvoiceIdentity.class);
        accounts = mock(AccountDirectory.class);
        when(identity.owner("Bearer test")).thenReturn(UUID.randomUUID());
        when(identity.owner(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        when(accounts.accountsOf("Bearer test")).thenReturn(List.of(
                new AccountDirectory.AccountInfo(account, "KCB", "Business", "***0167")));
        mvc = MockMvcBuilders.standaloneSetup(new ActivityController(identity, accounts, transactions)).build();
    }

    private Transaction credit() {
        Transaction tx = new Transaction();
        tx.setAccountId(account);
        tx.setAmount(new BigDecimal("1500.00"));
        tx.setCurrency("KES");
        tx.setTransactionType("CREDIT");
        tx.setCounterparty("John Doe (254711111111)");
        tx.setStatus("POSTED");
        tx.setTransactionDate(OffsetDateTime.parse("2026-10-07T09:30:00Z"));
        return tx;
    }

    @Test
    void listsTheUsersMoneyInAndOutWithTheBankAndSender() throws Exception {
        when(transactions.findByAccountIdInOrderByCreatedAtDesc(eq(Set.of(account)), any())).thenReturn(List.of(credit()));
        mvc.perform(get("/api/transactions/activity").header("Authorization", "Bearer test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bank").value("KCB"))
                .andExpect(jsonPath("$[0].maskedIdentifier").value("***0167"))
                .andExpect(jsonPath("$[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$[0].amount").value(1500.00))
                .andExpect(jsonPath("$[0].counterparty").value("John Doe (254711111111)"));
    }

    @Test
    void pollsForOnlyWhatArrivedSince() throws Exception {
        OffsetDateTime since = OffsetDateTime.parse("2026-10-07T09:00:00Z");
        when(transactions.findByAccountIdInAndCreatedAtAfterOrderByCreatedAtDesc(eq(Set.of(account)), eq(since), any()))
                .thenReturn(List.of());
        mvc.perform(get("/api/transactions/activity").param("since", "2026-10-07T09:00:00Z")
                        .header("Authorization", "Bearer test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        verify(transactions).findByAccountIdInAndCreatedAtAfterOrderByCreatedAtDesc(eq(Set.of(account)), eq(since), any());
    }

    @Test
    void refusesWithoutASignedInUser() throws Exception {
        mvc.perform(get("/api/transactions/activity")).andExpect(status().isUnauthorized());
        verify(transactions, never()).findByAccountIdInOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void aUserWithNoAccountsGetsAnEmptyFeed() throws Exception {
        when(accounts.accountsOf("Bearer test")).thenReturn(List.of());
        mvc.perform(get("/api/transactions/activity").header("Authorization", "Bearer test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
