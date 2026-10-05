package com.smi.identity_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateOrganizationRequest {

    @NotBlank(message = "Organisation name is required")
    @Size(min = 2, max = 150, message = "Organisation name must be between 2 and 150 characters")
    private String organizationName;

    /**
     * The owner is optional. Leave both owner fields out to save the organisation
     * on its own, then invite the owner later as a member with the OWNER role.
     * When either is given, both must be.
     */
    @Size(max = 100, message = "Owner name must be at most 100 characters")
    private String ownerName;

    @Email(message = "Owner email must be valid")
    private String ownerEmail;

    @NotBlank(message = "Business type is required")
    private String businessType;

    public CreateOrganizationRequest() {
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }
}
