package com.smi.identity_service.service;

import com.smi.identity_service.domain.Organization;
import com.smi.identity_service.domain.OrganizationMember;
import com.smi.identity_service.domain.User;
import com.smi.identity_service.dto.CreateOrganizationRequest;
import com.smi.identity_service.dto.InviteMemberRequest;
import com.smi.identity_service.dto.OrganizationMemberResponse;
import com.smi.identity_service.dto.OrganizationResponse;
import com.smi.identity_service.exception.OrganizationNotFoundException;
import com.smi.identity_service.exception.UserAlreadyMemberException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationInviteServiceTest {

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserService userService;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordResetService passwordResets;

    @InjectMocks
    private OrganizationInviteService inviteService;

    private Organization organization;
    private User owner;

    @BeforeEach
    void setUp() {
        organization = new Organization("Kilimani Properties", "kilimani-properties", "SME");
        organization.setId(UUID.randomUUID());
        owner = new User(UUID.randomUUID(), "James Otieno", "james@example.com", "hash");
    }

    private CreateOrganizationRequest createRequest() {
        CreateOrganizationRequest request = new CreateOrganizationRequest();
        request.setOrganizationName("Kilimani Properties");
        request.setOwnerName("James Otieno");
        request.setOwnerEmail("james@example.com");
        request.setBusinessType("SME");
        return request;
    }

    private InviteMemberRequest inviteRequest() {
        InviteMemberRequest request = new InviteMemberRequest();
        request.setName("Jane Doe");
        request.setEmail("jane@example.com");
        request.setRole("ADMIN");
        return request;
    }

    @Test
    @DisplayName("Creating an organisation for a new email creates the owner and emails a set-password link")
    void createsOrganisationWithNewOwner() {
        when(organizationService.createOrganizationRow("Kilimani Properties", "SME")).thenReturn(organization);
        when(userService.findByEmail("james@example.com")).thenReturn(Optional.empty());
        when(userService.createUser(eq("James Otieno"), eq("james@example.com"), anyString())).thenReturn(owner);
        when(passwordResets.setupLink(owner)).thenReturn("https://app.example/reset-password?token=abc");
        when(emailService.sendOrganizationOwnerSetup(eq("james@example.com"), eq("James Otieno"),
                eq("Kilimani Properties"), eq("https://app.example/reset-password?token=abc"))).thenReturn(true);
        when(organizationService.countActiveMembers(organization.getId())).thenReturn(1L);

        OrganizationResponse response = inviteService.createOrganizationWithOwner(createRequest());

        assertEquals(organization.getId(), response.getId());
        assertEquals(1, response.getMemberCount());
        assertTrue(response.getEmailSent());
        verify(organizationService).addMember(organization.getId(), owner, "OWNER", null);
    }

    @Test
    @DisplayName("Saving an organisation with no owner creates no account and sends no email")
    void savesOrganisationWithoutOwner() {
        CreateOrganizationRequest request = createRequest();
        request.setOwnerName(null);
        request.setOwnerEmail(null);
        when(organizationService.createOrganizationRow("Kilimani Properties", "SME")).thenReturn(organization);

        OrganizationResponse response = inviteService.createOrganizationWithOwner(request);

        assertEquals(organization.getId(), response.getId());
        assertEquals(0, response.getMemberCount());
        assertNull(response.getEmailSent());
        verifyNoInteractions(userService);
        verifyNoInteractions(emailService);
        verify(organizationService, never()).addMember(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("A half-filled owner is refused before the organisation is written")
    void halfFilledOwnerIsRefused() {
        CreateOrganizationRequest request = createRequest();
        request.setOwnerName("  ");

        assertThrows(IllegalArgumentException.class, () -> inviteService.createOrganizationWithOwner(request));

        verifyNoInteractions(organizationService);
        verifyNoInteractions(userService);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("No password is ever emailed: the owner gets a set-password link instead")
    void noPasswordGoesToEmail() {
        when(organizationService.createOrganizationRow(anyString(), anyString())).thenReturn(organization);
        when(userService.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userService.createUser(anyString(), anyString(), anyString())).thenReturn(owner);
        when(passwordResets.setupLink(owner)).thenReturn("https://app.example/reset-password?token=abc");
        when(organizationService.countActiveMembers(any())).thenReturn(1L);

        inviteService.createOrganizationWithOwner(createRequest());

        ArgumentCaptor<String> createdWith = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailedWith = ArgumentCaptor.forClass(String.class);
        verify(userService).createUser(anyString(), anyString(), createdWith.capture());
        verify(emailService).sendOrganizationOwnerSetup(anyString(), anyString(), anyString(), emailedWith.capture());
        assertNotEquals(createdWith.getValue(), emailedWith.getValue());
        assertFalse(emailedWith.getValue().contains(createdWith.getValue()));
        assertTrue(createdWith.getValue().length() >= 32);
    }

    @Test
    @DisplayName("An email failure does not stop the organisation and owner from being written")
    void emailFailureStillWritesOrganisation() {
        when(organizationService.createOrganizationRow(anyString(), anyString())).thenReturn(organization);
        when(userService.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userService.createUser(anyString(), anyString(), anyString())).thenReturn(owner);
        when(passwordResets.setupLink(owner)).thenReturn("https://app.example/reset-password?token=abc");
        when(emailService.sendOrganizationOwnerSetup(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(false);
        when(organizationService.countActiveMembers(any())).thenReturn(1L);

        OrganizationResponse response = inviteService.createOrganizationWithOwner(createRequest());

        assertFalse(response.getEmailSent());
        verify(organizationService).addMember(organization.getId(), owner, "OWNER", null);
    }

    @Test
    @DisplayName("An existing user is reused, their password untouched, and told they were added")
    void existingUserIsReusedWithoutNewPassword() {
        when(organizationService.getOrganization(organization.getId())).thenReturn(organization);
        when(userService.findByEmail("jane@example.com")).thenReturn(Optional.of(owner));
        when(organizationService.isAlreadyMember(organization.getId(), owner.getId())).thenReturn(false);
        when(organizationService.addMember(organization.getId(), owner, "ADMIN", null))
                .thenReturn(new OrganizationMember(organization, owner, "ADMIN", null));

        OrganizationMemberResponse response = inviteService.inviteMember(organization.getId(), inviteRequest());

        verify(userService, never()).createUser(anyString(), anyString(), anyString());
        verify(emailService).sendExistingUserAddedToOrganization(anyString(), anyString(), anyString(), eq("ADMIN"));
        verify(emailService, never()).sendMemberSetup(anyString(), anyString(), anyString(), anyString(), anyString());
        assertEquals(owner.getId(), response.getUserId());
    }

    @Test
    @DisplayName("Inviting someone who is already a member is rejected")
    void duplicateMembershipIsRejected() {
        when(organizationService.getOrganization(organization.getId())).thenReturn(organization);
        when(userService.findByEmail("jane@example.com")).thenReturn(Optional.of(owner));
        when(organizationService.isAlreadyMember(organization.getId(), owner.getId())).thenReturn(true);

        assertThrows(UserAlreadyMemberException.class,
                () -> inviteService.inviteMember(organization.getId(), inviteRequest()));

        verify(organizationService, never()).addMember(any(), any(), anyString(), any());
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Inviting into an unknown organisation fails before any user is created")
    void unknownOrganisationIsRejected() {
        UUID missing = UUID.randomUUID();
        when(organizationService.getOrganization(missing))
                .thenThrow(new OrganizationNotFoundException("Organization not found with id: " + missing));

        assertThrows(OrganizationNotFoundException.class, () -> inviteService.inviteMember(missing, inviteRequest()));

        verifyNoInteractions(userService);
        verifyNoInteractions(emailService);
    }
}
