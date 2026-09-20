package io.smartmoney.api.bankintegration;

import java.time.Instant;

public record TokenSnapshot(TokenStatus status, Integer expiresInMinutes, Instant refreshedAt) {

    public static TokenSnapshot unknown() {
        return new TokenSnapshot(TokenStatus.UNKNOWN, null, null);
    }
}
