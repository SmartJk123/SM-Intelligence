package com.smi.identity_service.controller;

import com.smi.identity_service.dto.CreateOrganizationRequest;
import com.smi.identity_service.dto.InviteMemberRequest;
import com.smi.identity_service.dto.OrganizationMemberResponse;
import com.smi.identity_service.dto.OrganizationResponse;
import com.smi.identity_service.service.OrganizationInviteService;
import com.smi.identity_service.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrganizationInviteService organizationInviteService;

    public OrganizationController(OrganizationService organizationService,
                                   OrganizationInviteService organizationInviteService) {
        this.organizationService = organizationService;
        this.organizationInviteService = organizationInviteService;
    }

    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> listOrganizations() {
        List<OrganizationResponse> response = organizationService.listOrganizations().stream()
                .map(org -> OrganizationResponse.fromOrganization(
                        org, (int) organizationService.countActiveMembers(org.getId()), null))
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<OrganizationMemberResponse>> listMembers(@PathVariable UUID id) {
        List<OrganizationMemberResponse> response = organizationService.listMembers(id).stream()
                .map(member -> OrganizationMemberResponse.fromMember(member, false))
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationResponse response = organizationInviteService.createOrganizationWithOwner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<OrganizationMemberResponse> inviteMember(@PathVariable UUID id,
                                                                     @Valid @RequestBody InviteMemberRequest request) {
        OrganizationMemberResponse response = organizationInviteService.inviteMember(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
