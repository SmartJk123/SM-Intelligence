package com.smi.identity_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email address is required")
        @Email(message = "Enter a valid email address")
        String emailAddress) {
}
