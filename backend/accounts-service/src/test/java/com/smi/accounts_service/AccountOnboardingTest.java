package com.smi.accounts_service;

import com.smi.accounts_service.service.AccountIdentity;
import com.smi.accounts_service.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountOnboardingTest {
    @LocalServerPort int port;
    @MockitoBean AccountIdentity identity;
    @Autowired AccountRepository accounts;
    final HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> request(String path, String method, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
            .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", token);
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void savesRealAccountAndEnforcesOwnership() throws Exception {
        var owner = UUID.randomUUID(); var other = UUID.randomUUID();
        when(identity.owner("Bearer owner")).thenReturn(owner);
        when(identity.owner("Bearer other")).thenReturn(other);
        when(identity.owner(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String body = """
            {"bank":"KCB","accountName":"My credit account","accountNumber":"12345678",
             "cardType":"credit","balance":1200.50,"balanceDate":"2026-01-01","currency":"KES"}
            """;
        assertEquals(401, request("/api/accounts/manual", "POST", null, body).statusCode());
        var saved = request("/api/accounts/manual", "POST", "Bearer owner", body);
        assertEquals(201, saved.statusCode(), saved.body());
        var account = accounts.findByUserId(owner).getFirst();
        assertEquals(0, account.getAvailableBalance().signum());
        assertEquals(0, account.getCreditOutstanding().compareTo(new java.math.BigDecimal("1200.50")));
        assertEquals("DISCONNECTED", account.getConnectionStatus());
        assertEquals("2026-01-01", account.getLastUpdated().toLocalDate().toString());
        assertFalse(saved.body().contains("12345678"));
        assertEquals(409, request("/api/accounts/manual", "POST", "Bearer owner", body).statusCode());
        assertEquals("[]", request("/api/accounts?userId="+owner, "GET", "Bearer other", null).body());
        for (String method : new String[]{"GET", "DELETE"})
            assertEquals(404, request("/api/accounts/"+account.getId(), method, "Bearer other", null).statusCode());
        assertEquals(404, request("/api/accounts/"+account.getId()+"/balance", "PATCH", "Bearer other", "{\"availableBalance\":0}").statusCode());
        assertEquals(400, request("/api/accounts/manual", "POST", "Bearer owner", body.replace("1200.50", "-1")).statusCode());
        assertEquals(400, request("/api/accounts/manual", "POST", "Bearer owner", body.replace("2026-01-01", "2999-01-01")).statusCode());
        assertEquals(1, accounts.findByUserId(owner).size());
    }

    @Test void refusesAnAccountNumberAnotherUserHolds() throws Exception {
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        when(identity.owner("Bearer first")).thenReturn(first);
        when(identity.owner("Bearer second")).thenReturn(second);
        String body = """
            {"bank":"NCBA","accountName":"Salary","accountNumber":"55556666",
             "cardType":"debit","balance":10,"balanceDate":"2026-01-01","currency":"KES"}
            """;
        assertEquals(201, request("/api/accounts/manual", "POST", "Bearer first", body).statusCode());
        var taken = request("/api/accounts/manual", "POST", "Bearer second", body);
        assertEquals(409, taken.statusCode());
        assertTrue(taken.body().contains("another user"), taken.body());
        // The same number at a different bank is a different account.
        assertEquals(201, request("/api/accounts/manual", "POST", "Bearer second", body.replace("NCBA", "KCB")).statusCode());
    }

    @Test void refusesASelfEnteredNumberAlreadyLinkedByTheBank() throws Exception {
        var linked = UUID.randomUUID(); var other = UUID.randomUUID();
        when(identity.owner("Bearer other")).thenReturn(other);
        accounts.saveAndFlush(new com.smi.accounts_service.domain.Account(linked, "77778888", "Business", "Equity",
            "DEPOSIT", "***8888", "KES", java.math.BigDecimal.ZERO));
        String body = """
            {"bank":"Equity","accountName":"Mine","accountNumber":"77778888",
             "cardType":"debit","balance":10,"balanceDate":"2026-01-01","currency":"KES"}
            """;
        assertEquals(409, request("/api/accounts/manual", "POST", "Bearer other", body).statusCode());
    }

    @Test void matchesSelfEnteredAccountsSavedWithTheOlderOwnerFingerprint() throws Exception {
        var legacyOwner = UUID.randomUUID(); var other = UUID.randomUUID();
        when(identity.owner("Bearer other")).thenReturn(other);
        var legacyFingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest((legacyOwner + ":Stanbic:99990000").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var legacy = new com.smi.accounts_service.domain.Account(legacyOwner, legacyFingerprint, "Old", "Stanbic",
            "DEPOSIT", "•••• 0000", "KES", java.math.BigDecimal.ZERO);
        legacy.setDataSource("MANUAL");
        accounts.saveAndFlush(legacy);
        String body = """
            {"bank":"Stanbic","accountName":"Mine","accountNumber":"99990000",
             "cardType":"debit","balance":10,"balanceDate":"2026-01-01","currency":"KES"}
            """;
        assertEquals(409, request("/api/accounts/manual", "POST", "Bearer other", body).statusCode());
    }
}
