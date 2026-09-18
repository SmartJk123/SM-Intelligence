package com.smi.identity_service.repository;

import com.smi.identity_service.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository interface for managing User entity persistence in identity-service.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds an active User entity by unique email address.
     */
    Optional<User> findByEmailAddressAndDeletedAtIsNull(String emailAddress);

    /**
     * Finds a User entity by unique email address.
     */
    Optional<User> findByEmailAddress(String emailAddress);

    /**
     * Finds a User entity by phone number.
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Checks if an active user exists with the given phone number.
     */
    boolean existsByPhoneNumber(String phoneNumber);
}
