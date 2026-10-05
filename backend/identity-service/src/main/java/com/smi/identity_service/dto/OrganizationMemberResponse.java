package com.smi.identity_service.dto;

import com.smi.identity_service.domain.OrganizationMember;

import java.time.OffsetDateTime;
import java.util.UUID;

public class OrganizationMemberResponse {

    private UUID userId;
    private String name;
    private String email;
    private String role;
    private String memberStatus;
    private OffsetDateTime joinedAt;

    /** True when the notification email (credentials or "added to org") was sent. */
    private boolean emailSent;

    public OrganizationMemberResponse() {
    }

    public static OrganizationMemberResponse fromMember(OrganizationMember member, boolean emailSent) {
        OrganizationMemberResponse response = new OrganizationMemberResponse();
        response.setUserId(member.getUser().getId());
        response.setName(member.getUser().getName());
        response.setEmail(member.getUser().getEmailAddress());
        response.setRole(member.getRole());
        response.setMemberStatus(member.getMemberStatus());
        response.setJoinedAt(member.getJoinedAt());
        response.setEmailSent(emailSent);
        return response;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMemberStatus() {
        return memberStatus;
    }

    public void setMemberStatus(String memberStatus) {
        this.memberStatus = memberStatus;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public boolean isEmailSent() {
        return emailSent;
    }

    public void setEmailSent(boolean emailSent) {
        this.emailSent = emailSent;
    }
}
