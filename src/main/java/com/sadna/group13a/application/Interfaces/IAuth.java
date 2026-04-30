package com.sadna.group13a.application.Interfaces;

public interface IAuth {

    /**
     * Generates a secure token for an authenticated user.
     *
     * @param userId The unique identifier of the user.
     * @return A string representing the signed JWT.
     */
    String generateToken(String userId);

    /**
     * Validates whether a provided token is genuine and hasn't expired.
     *
     * @param token The token string provided by the client.
     * @return true if valid, false if tampered with or expired.
     */
    boolean validateToken(String token);

    /**
     * Extracts the user's unique identifier from a valid token.
     *
     * @param token The token string provided by the client.
     * @return The userId embedded inside the token.
     */
    String extractUserId(String token);
}