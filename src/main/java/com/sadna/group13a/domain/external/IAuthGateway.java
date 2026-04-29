package com.sadna.group13a.domain.external;

/**
 * Interface for core authentication operations that the Domain or Application layer might need.
 */
public interface IAuthGateway {

    /**
     * Hashes a raw password for secure storage.
     *
     * @param rawPassword The plain text password.
     * @return The securely hashed password.
     */
    String hashPassword(String rawPassword);

    /**
     * Verifies a raw password against a previously stored hash.
     *
     * @param rawPassword    The plain text password.
     * @param hashedPassword The stored hash.
     * @return true if the password matches the hash.
     */
    boolean verifyPassword(String rawPassword, String hashedPassword);
    
    /**
     * Generates a secure session token (e.g. JWT) for an authenticated user.
     *
     * @param userId The authenticated user ID.
     * @param role   The role of the user (e.g. MEMBER, ADMIN).
     * @return The generated token string.
     */
    String generateToken(String userId, String role);
}
