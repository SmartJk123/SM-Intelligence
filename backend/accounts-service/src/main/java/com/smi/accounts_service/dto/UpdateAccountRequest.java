package com.smi.accounts_service.dto;

import jakarta.validation.constraints.Size;

public class UpdateAccountRequest {

    @Size(min = 1, max = 150, message = "Account name must be between 1 and 150 characters")
    private String accountName;

    public UpdateAccountRequest() {
    }

    public UpdateAccountRequest(String accountName) {
        this.accountName = accountName;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }
}
