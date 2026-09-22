package com.smi.identity_service.dto;

import com.smi.identity_service.domain.User;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only summary of a user for the admin interface. Never includes the
 * password hash.
 */
public class UserSummaryResponse {

    private UUID id;
    private String name;
    private String emailAddress;
    private String phoneNumber;
    private String accountType;
    private String organizationName;
    private String businessType;
    private String industry;
    private OffsetDateTime createdAt;

    public UserSummaryResponse() {
    }

    public static UserSummaryResponse fromUser(User user) {
        UserSummaryResponse response = new UserSummaryResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmailAddress(user.getEmailAddress());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setAccountType(user.getAccountType());
        response.setOrganizationName(user.getOrganizationName());
        response.setBusinessType(user.getBusinessType());
        response.setIndustry(user.getIndustry());
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
