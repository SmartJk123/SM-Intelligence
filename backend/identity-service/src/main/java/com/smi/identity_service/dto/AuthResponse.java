package com.smi.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class AuthResponse {

    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;
    private UUID userId;
    private String name;
    private String emailAddress;
    private String phoneNumber;
    private String accountType;
    /** USER or PLATFORM_ADMIN, so the admin interface can refuse a customer account. */
    private String role;
    private String refreshToken;
    private Boolean mfaRequired = false;
    private String mfaToken;

    public AuthResponse() {
    }

    public static AuthResponse mfaChallenge(String mfaToken) {
        AuthResponse response = new AuthResponse();
        response.setMfaRequired(true);
        response.setMfaToken(mfaToken);
        return response;
    }

    public AuthResponse(String token, long expiresIn, UUID userId, String name, String emailAddress, String accountType) {
        this(token, expiresIn, userId, name, emailAddress, null, accountType, null, null);
    }

    public AuthResponse(String token, long expiresIn, UUID userId, String name, String emailAddress, String phoneNumber, String accountType) {
        this(token, expiresIn, userId, name, emailAddress, phoneNumber, accountType, null, null);
    }

    public AuthResponse(String token, long expiresIn, UUID userId, String name, String emailAddress, String phoneNumber, String accountType, String role) {
        this(token, expiresIn, userId, name, emailAddress, phoneNumber, accountType, role, null);
    }

    public AuthResponse(String token, long expiresIn, UUID userId, String name, String emailAddress, String phoneNumber, String accountType, String role, String refreshToken) {
        this.token = token;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.userId = userId;
        this.name = name;
        this.emailAddress = emailAddress;
        this.phoneNumber = phoneNumber;
        this.accountType = accountType;
        this.role = role;
        this.refreshToken = refreshToken;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    @JsonProperty("userId")
    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    @JsonProperty("id")
    public UUID getId() {
        return userId;
    }

    public void setId(UUID id) {
        this.userId = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("emailAddress")
    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    @JsonProperty("email")
    public String getEmail() {
        return emailAddress;
    }

    public void setEmail(String email) {
        if (this.emailAddress == null) {
            this.emailAddress = email;
        }
    }

    @JsonProperty("phoneNumber")
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @JsonProperty("refreshToken")
    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Boolean getMfaRequired() {
        return mfaRequired;
    }

    public void setMfaRequired(Boolean mfaRequired) {
        this.mfaRequired = mfaRequired;
    }

    public String getMfaToken() {
        return mfaToken;
    }

    public void setMfaToken(String mfaToken) {
        this.mfaToken = mfaToken;
    }
}
