package com.smi.identity_service.dto;

import java.util.UUID;

/**
 * Standard RFC 7662 OAuth 2.0 Token Introspection response payload.
 */
public record TokenIntrospectionResponse(
        boolean active,
        UUID userId,
        String email,
        String role,
        String status,
        String accountType,
        Long exp
) {
    public static TokenIntrospectionResponse inactive() {
        return new TokenIntrospectionResponse(false, null, null, null, null, null, null);
    }

    public static TokenIntrospectionResponse active(
            UUID userId, String email, String role, String status, String accountType, long expSeconds) {
        return new TokenIntrospectionResponse(true, userId, email, role, status, accountType, expSeconds);
    }
}
