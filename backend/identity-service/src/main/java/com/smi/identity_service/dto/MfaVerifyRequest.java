package com.smi.identity_service.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaVerifyRequest(
        @NotBlank(message = "Verification code is required")
        String code,
        String mfaToken
) {}
