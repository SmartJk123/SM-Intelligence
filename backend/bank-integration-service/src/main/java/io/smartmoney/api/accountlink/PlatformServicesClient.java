package io.smartmoney.api.accountlink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Calls accounts-service and transactions-service, which hold what the
 * customer's web dashboard shows. They run on the same private network as
 * this service, so they must never be exposed publicly. accounts-service
 * requires every caller to be a signed-in user except this one: it trusts
 * INTERNAL_SERVICE_TOKEN as proof this is bank-integration-service linking or
 * closing a customer's account on their behalf, since there is no user
 * sitting at a browser to hold a session token for that action.
 */
@Component
public class PlatformServicesClient {

    /** The outcome of recording a movement. A duplicate is success: it is already there. */
    public enum Recorded { CREATED, ALREADY_PRESENT }

    private final RestClient accounts;
    private final RestClient transactions;
    private final String internalServiceToken;
    private final ObjectMapper mapper;

    public PlatformServicesClient(
            @Value("${smartmoney.platform-services.accounts-url:http://localhost:8082}") String accountsUrl,
            @Value("${smartmoney.platform-services.transactions-url:http://localhost:8083}") String transactionsUrl,
            @Value("${INTERNAL_SERVICE_TOKEN:}") String internalServiceToken,
            ObjectMapper mapper) {
        // HttpURLConnection (SimpleClientHttpRequestFactory) cannot send PATCH at
        // all, which setStatus() needs to activate or close a customer's account.
        var factory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.accounts = RestClient.builder().baseUrl(accountsUrl).requestFactory(factory).build();
        this.transactions = RestClient.builder().baseUrl(transactionsUrl).requestFactory(factory).build();
        this.internalServiceToken = internalServiceToken;
        this.mapper = mapper;
    }

    /**
     * Creates the customer's account, or returns the one accounts-service already
     * holds for this institution and number when it belongs to the same customer
     * (a link removed and made again). It is set ACTIVE either way.
     */
    public String ensureAccount(String userId, String institution, String accountNumber, String accountName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("providerAccountId", accountNumber);
        body.put("accountName", accountName);
        body.put("institution", institution);
        body.put("accountType", "DEPOSIT");
        body.put("maskedIdentifier", mask(accountNumber));
        body.put("currency", "KES");
        body.put("dataSource", "BANK_API");
        try {
            String raw = accounts.post().uri("/api/accounts")
                    .contentType(MediaType.APPLICATION_JSON).header("X-Internal-Token", internalServiceToken)
                    .body(body)
                    .retrieve().body(String.class);
            return mapper.readTree(raw).path("id").asText();
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                throw unavailable("accounts-service refused the account: " + error.getStatusCode().value());
            }
            String existing = findAccount(userId, institution, accountNumber)
                    .orElseThrow(() -> new AccountLinkException(HttpStatus.CONFLICT,
                            "accounts-service already holds this account for a different customer"));
            setStatus(existing, "ACTIVE");
            return existing;
        } catch (RestClientException error) {
            throw unavailable("accounts-service cannot be reached");
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw unavailable("accounts-service returned a response that could not be read");
        }
    }

    /** Marks the account CLOSED. Its history stays in transactions-service. */
    public void closeAccount(String accountId) {
        try {
            setStatus(accountId, "CLOSED");
        } catch (RestClientException error) {
            throw unavailable("accounts-service cannot be reached");
        }
    }

    public Recorded recordTransaction(String accountId, BigDecimal amount, String currency, String type,
                                      String providerReference, String description, Instant bookedAt,
                                      String counterparty) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", accountId);
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("transactionType", type);
        body.put("paymentMethod", "BANK_TRANSFER");
        body.put("providerReference", providerReference);
        body.put("description", description);
        body.put("transactionDate", bookedAt.atOffset(ZoneOffset.UTC).toString());
        if (counterparty != null && !counterparty.isBlank()) {
            body.put("counterparty", counterparty);
        }
        try {
            transactions.post().uri("/api/transactions")
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
            return Recorded.CREATED;
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                return Recorded.ALREADY_PRESENT;
            }
            throw new IllegalStateException("transactions-service refused the movement: HTTP "
                    + error.getStatusCode().value() + " " + error.getResponseBodyAsString());
        }
    }

    private Optional<String> findAccount(String userId, String institution, String accountNumber) {
        String raw = accounts.get().uri(uri -> uri.path("/api/accounts").queryParam("userId", userId).build())
                .header("X-Internal-Token", internalServiceToken)
                .retrieve().body(String.class);
        JsonNode list;
        try {
            list = raw == null ? null : mapper.readTree(raw);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw unavailable("accounts-service returned a response that could not be read");
        }
        if (list == null) {
            return Optional.empty();
        }
        for (JsonNode account : list) {
            if (institution.equals(account.path("institution").asText())
                    && accountNumber.equals(account.path("providerAccountId").asText())) {
                return Optional.of(account.path("id").asText());
            }
        }
        return Optional.empty();
    }

    private void setStatus(String accountId, String status) {
        accounts.patch().uri("/api/accounts/{id}/status", accountId)
                .contentType(MediaType.APPLICATION_JSON).header("X-Internal-Token", internalServiceToken)
                .body(Map.of("accountStatus", status))
                .retrieve().toBodilessEntity();
    }

    private static String mask(String accountNumber) {
        return accountNumber.length() <= 4 ? accountNumber : "***" + accountNumber.substring(accountNumber.length() - 4);
    }

    private static AccountLinkException unavailable(String message) {
        return new AccountLinkException(HttpStatus.BAD_GATEWAY, message);
    }
}
