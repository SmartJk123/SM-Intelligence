package com.smi.transactions_service.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The signed-in user's accounts, read from accounts-service with the user's own
 * token. accounts-service answers only that user's accounts, so the activity
 * feed can never include someone else's money.
 */
@Component
public class AccountDirectory {

    /** What the feed shows about the account a movement belongs to. */
    public record AccountInfo(UUID id, String institution, String accountName, String maskedIdentifier) {
    }

    /** The fields of an accounts-service account this feed needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AccountJson(UUID id, String institution, String accountName, String maskedIdentifier, String accountStatus) {
    }

    private final RestClient client;

    public AccountDirectory(@Value("${ACCOUNTS_SERVICE_URL:http://${ACCOUNTS_SERVICE_HOSTPORT:localhost:8082}}") String accountsUrl) {
        var factory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder().baseUrl(accountsUrl).requestFactory(factory).build();
    }

    public List<AccountInfo> accountsOf(String authorization) {
        List<AccountJson> found;
        try {
            found = client.get().uri("/api/accounts").header("Authorization", authorization)
                    .retrieve().body(new ParameterizedTypeReference<List<AccountJson>>() { });
        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Accounts service unavailable");
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Accounts service unavailable");
        }
        return (found == null ? List.<AccountJson>of() : found).stream()
                .filter(account -> account.id() != null)
                .filter(account -> account.accountStatus() == null || "ACTIVE".equalsIgnoreCase(account.accountStatus()))
                .map(account -> new AccountInfo(account.id(), account.institution(), account.accountName(),
                        account.maskedIdentifier()))
                .toList();
    }
}
