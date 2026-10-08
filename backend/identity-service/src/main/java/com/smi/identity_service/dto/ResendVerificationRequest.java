package com.smi.identity_service.dto;

import jakarta.validation.constraints.Email;

public record ResendVerificationRequest(
        @Email(message = "Please provide a valid email address")
        String emailAddress
) {}
