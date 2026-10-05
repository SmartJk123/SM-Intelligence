package com.smi.identity_service.repository;

import com.smi.identity_service.domain.OrganizationMember;
import com.smi.identity_service.domain.OrganizationMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository interface for managing OrganizationMember entity persistence.
 */
@Repository
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, OrganizationMemberId> {

    List<OrganizationMember> findByIdOrganizationId(UUID organizationId);

    boolean existsByIdOrganizationIdAndIdUserId(UUID organizationId, UUID userId);

    long countByIdOrganizationIdAndMemberStatus(UUID organizationId, String memberStatus);
}
