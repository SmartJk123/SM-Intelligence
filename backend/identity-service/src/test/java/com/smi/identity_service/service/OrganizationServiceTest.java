package com.smi.identity_service.service;

import com.smi.identity_service.domain.Organization;
import com.smi.identity_service.exception.OrganizationNotFoundException;
import com.smi.identity_service.repository.OrganizationMemberRepository;
import com.smi.identity_service.repository.OrganizationRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @InjectMocks
    private OrganizationService service;

    @Test
    @DisplayName("Slug is derived from the name")
    void slugComesFromName() {
        when(organizationRepository.existsBySlug("kilimani-properties-ltd")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));

        Organization created = service.createOrganizationRow("  Kilimani Properties Ltd ", "SME");

        assertEquals("kilimani-properties-ltd", created.getSlug());
        assertEquals("Kilimani Properties Ltd", created.getName());
        assertEquals("SME", created.getBusinessType());
    }

    @Test
    @DisplayName("A slug collision gets a numeric suffix")
    void slugCollisionGetsSuffix() {
        when(organizationRepository.existsBySlug("acme")).thenReturn(true);
        when(organizationRepository.existsBySlug(argThat(s -> s != null && s.startsWith("acme-")))).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));

        Organization created = service.createOrganizationRow("Acme", "SME");

        assertTrue(created.getSlug().matches("acme-\\d{6}"));
    }

    @Test
    @DisplayName("Gives up after five slug collisions")
    void slugGenerationIsBounded() {
        when(organizationRepository.existsBySlug(anyString())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.createOrganizationRow("Acme", "SME"));
        verify(organizationRepository, never()).save(any(Organization.class));
    }

    @Test
    @DisplayName("A name with no letters or digits still gets a usable slug")
    void symbolOnlyNameFallsBack() {
        when(organizationRepository.existsBySlug("organisation")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));

        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        service.createOrganizationRow("!!!", "SME");
        verify(organizationRepository).save(captor.capture());
        assertEquals("organisation", captor.getValue().getSlug());
    }

    @Test
    @DisplayName("Unknown organisation id throws not found")
    void unknownOrganisationThrows() {
        UUID id = UUID.randomUUID();
        when(organizationRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class, () -> service.getOrganization(id));
    }
}
