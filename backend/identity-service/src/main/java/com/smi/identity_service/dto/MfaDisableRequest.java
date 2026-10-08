package com.smi.identity_service.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaDisableRequest(
        @NotBlank(message = "Password is required")
        String password,
        @NotBlank(message = "Verification code is required")
        String code
) {}
