package com.smi.identity_service.dto;

import com.smi.identity_service.domain.Organization;

import java.time.OffsetDateTime;
import java.util.UUID;

public class OrganizationResponse {

    private UUID id;
    private String name;
    private String slug;
    private String status;
    private String businessType;
    private int memberCount;
    private OffsetDateTime createdAt;

    /** Only meaningful on a create/invite response; true when the credentials email was sent. */
    private Boolean emailSent;

    public OrganizationResponse() {
    }

    public static OrganizationResponse fromOrganization(Organization org, int memberCount, Boolean emailSent) {
        OrganizationResponse response = new OrganizationResponse();
        response.setId(org.getId());
        response.setName(org.getName());
        response.setSlug(org.getSlug());
        response.setStatus(org.getStatus());
        response.setBusinessType(org.getBusinessType());
        response.setMemberCount(memberCount);
        response.setCreatedAt(org.getCreatedAt());
        response.setEmailSent(emailSent);
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

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getEmailSent() {
        return emailSent;
    }

    public void setEmailSent(Boolean emailSent) {
        this.emailSent = emailSent;
    }
}
