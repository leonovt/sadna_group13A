package com.sadna.group13a.domain.user;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for the User aggregate.
 * Defined in the Domain layer; implemented in Infrastructure layer.
 * Handles all User subtypes (Guest, Member, Admin) through a single interface.
 */
public interface IUserRepository {

    /**
     * Find a user by their unique ID.
     * Returns the concrete subtype (Guest, Member, or Admin).
     */
    Optional<User> findById(String id);

    /**
     * Find a user by username (used during login).
     */
    Optional<User> findByUsername(String username);

    /**
     * Persist a user (create or update).
     */
    void save(User user);

    /**
     * Remove a user from the repository.
     */
    void delete(String id);

    /**
     * Return all users in the system.
     */
    List<User> findAll();

    /**
     * Check if a username is already taken.
     */
    boolean existsByUsername(String username);
}
