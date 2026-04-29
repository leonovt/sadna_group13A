package com.sadna.group13a.domain.shared;

/**
 * Base class for all domain-specific exceptions.
 * Application layer catches these to return appropriate error responses.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
