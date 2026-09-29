package com.smi.accounts_service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Pattern;

public class UpdateStatusRequest {

    @Pattern(regexp = "^(ACTIVE|CLOSED|RESTRICTED)$", message = "accountStatus must be ACTIVE, CLOSED, or RESTRICTED")
    @JsonAlias({"account_status", "status"})
    private String accountStatus;

    @Pattern(regexp = "^(CONNECTED|SYNCING|DISCONNECTED|ACTION_REQUIRED)$", message = "connectionStatus must be CONNECTED, SYNCING, DISCONNECTED, or ACTION_REQUIRED")
    @JsonAlias({"connection_status"})
    private String connectionStatus;

    public UpdateStatusRequest() {
    }

    public UpdateStatusRequest(String accountStatus, String connectionStatus) {
        this.accountStatus = accountStatus != null ? accountStatus.trim().toUpperCase() : null;
        this.connectionStatus = connectionStatus != null ? connectionStatus.trim().toUpperCase() : null;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus != null ? accountStatus.trim().toUpperCase() : null;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus != null ? connectionStatus.trim().toUpperCase() : null;
    }
}
