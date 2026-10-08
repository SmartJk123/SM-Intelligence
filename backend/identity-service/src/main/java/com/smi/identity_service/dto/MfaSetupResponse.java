package com.smi.identity_service.dto;

import java.util.List;

public record MfaSetupResponse(
        String secret,
        String otpauthUri,
        List<String> backupCodes
) {}
