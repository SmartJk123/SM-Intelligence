package com.smi.identity_service.service;

import com.smi.identity_service.domain.Organization;
import com.smi.identity_service.domain.OrganizationMember;
import com.smi.identity_service.domain.User;
import com.smi.identity_service.exception.OrganizationNotFoundException;
import com.smi.identity_service.repository.OrganizationMemberRepository;
import com.smi.identity_service.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Persistence-only service for organizations and their membership.
 *
 * Deliberately has no dependency on EmailService or UserService: every method
 * here is a plain database write, so the whole class can stay transactional
 * without risking a transaction silently becoming a no-op through self
 * invocation, and without holding a DB connection open for an SMTP call.
 * Orchestration that spans user creation and email lives in
 * {@link OrganizationInviteService} instead.
 */
@Service
@Transactional
public class OrganizationService {

    private static final int MAX_SLUG_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    public OrganizationService(OrganizationRepository organizationRepository,
                                OrganizationMemberRepository organizationMemberRepository) {
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
    }

    public Organization createOrganizationRow(String name, String businessType) {
        String slug = generateUniqueSlug(name);
        Organization organization = new Organization(name.trim(), slug, businessType);
        return organizationRepository.save(organization);
    }

    public OrganizationMember addMember(UUID organizationId, User user, String role, UUID invitedByUserId) {
        Organization organization = getOrganization(organizationId);
        OrganizationMember member = new OrganizationMember(organization, user, role, invitedByUserId);
        return organizationMemberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public Organization getOrganization(UUID id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new OrganizationNotFoundException("Organization not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Organization> listOrganizations() {
        return organizationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<OrganizationMember> listMembers(UUID organizationId) {
        return organizationMemberRepository.findByIdOrganizationId(organizationId);
    }

    @Transactional(readOnly = true)
    public long countActiveMembers(UUID organizationId) {
        return organizationMemberRepository.countByIdOrganizationIdAndMemberStatus(organizationId, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public boolean isAlreadyMember(UUID organizationId, UUID userId) {
        return organizationMemberRepository.existsByIdOrganizationIdAndIdUserId(organizationId, userId);
    }

    private String generateUniqueSlug(String name) {
        String base = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) {
            base = "organisation";
        }

        String candidate = base;
        for (int attempt = 0; attempt < MAX_SLUG_ATTEMPTS; attempt++) {
            if (!organizationRepository.existsBySlug(candidate)) {
                return candidate;
            }
            candidate = base + "-" + (100000 + RANDOM.nextInt(900000));
        }
        throw new IllegalStateException("Could not generate a unique slug for organisation: " + name);
    }
}
