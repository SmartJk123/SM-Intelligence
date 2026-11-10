package com.smi.audit_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class AuditIdentity {

    private final RestClient client;
    private final String internalServiceToken;

    public AuditIdentity(
            @Value("${IDENTITY_SERVICE_URL:http://${IDENTITY_SERVICE_HOSTPORT:localhost:8081}}") String identityUrl,
            @Value("${INTERNAL_SERVICE_TOKEN:}") String internalServiceToken) {
        var factory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl(identityUrl).requestFactory(factory).build();
        this.internalServiceToken = internalServiceToken;
    }

    public boolean isInternalService(String providedToken) {
        return internalServiceToken != null && !internalServiceToken.isBlank()
                && internalServiceToken.equals(providedToken);
    }

    public UUID resolveActor(String authorization, String xUserId, UUID paramUserId) {
        if (paramUserId != null) {
            return paramUserId;
        }
        if (xUserId != null && !xUserId.isBlank()) {
            try {
                return UUID.fromString(xUserId.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return owner(authorization);
        }
        return null;
    }

    public UUID owner(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        }
        try {
            var profile = client.get().uri("/api/auth/me").header("Authorization", authorization).retrieve().body(Map.class);
            if (profile == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User profile not found");
            }
            Object idVal = profile.get("id");
            if (idVal == null) {
                idVal = profile.get("userId");
            }
            if (idVal == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User id missing in profile");
            }
            return UUID.fromString(idVal.toString());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity service unavailable");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity service unavailable");
        }
    }
}
