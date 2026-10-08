package com.smi.identity_service.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for renewing an expired access token using a refresh token.
 */
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
}
