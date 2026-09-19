package com.smi.accounts_service.dto;

import jakarta.validation.constraints.Pattern;

public class UpdateStatusRequest {

    @Pattern(regexp = "^(ACTIVE|CLOSED|RESTRICTED)$", message = "accountStatus must be ACTIVE, CLOSED, or RESTRICTED")
    private String accountStatus;

    @Pattern(regexp = "^(CONNECTED|SYNCING|DISCONNECTED|ACTION_REQUIRED)$", message = "connectionStatus must be CONNECTED, SYNCING, DISCONNECTED, or ACTION_REQUIRED")
    private String connectionStatus;

    public UpdateStatusRequest() {
    }

    public UpdateStatusRequest(String accountStatus, String connectionStatus) {
        this.accountStatus = accountStatus;
        this.connectionStatus = connectionStatus;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }
}
