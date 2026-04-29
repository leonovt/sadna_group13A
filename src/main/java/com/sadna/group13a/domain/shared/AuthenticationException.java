package com.sadna.group13a.domain.shared;

/**
 * Thrown when an authentication operation fails
 * (e.g., wrong password, invalid token, expired session).
 */
public class AuthenticationException extends DomainException {

    public AuthenticationException(String message) {
        super(message);
    }
}
