package com.smi.identity_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email address is required")
    @Email(message = "Email address must be valid")
    private String emailAddress;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be at least 8 characters long")
    private String password;

    private String phoneNumber;

    @Pattern(regexp = "^(INDIVIDUAL|ORGANIZATION)$", message = "Invalid account type")
    private String accountType = "INDIVIDUAL";

    private String organizationName;

    public RegisterRequest() {
    }

    public RegisterRequest(String name, String emailAddress, String password, String phoneNumber) {
        this.name = name;
        this.emailAddress = emailAddress;
        this.password = password;
        this.phoneNumber = phoneNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }
}
