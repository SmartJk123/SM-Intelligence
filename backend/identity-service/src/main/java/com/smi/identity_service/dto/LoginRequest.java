package com.smi.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Email address is required")
    @Email(message = "Email address must be valid")
    @JsonProperty("emailAddress")
    @JsonAlias({"email", "email_address"})
    private String emailAddress;

    @NotBlank(message = "Password is required")
    @JsonProperty("password")
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String emailAddress, String password) {
        this.emailAddress = emailAddress;
        this.password = password;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getEmail() {
        return emailAddress;
    }

    public void setEmail(String email) {
        if (this.emailAddress == null) {
            this.emailAddress = email;
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
