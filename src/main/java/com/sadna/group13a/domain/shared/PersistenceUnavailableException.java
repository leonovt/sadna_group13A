package com.sadna.group13a.domain.shared;

/**
 * Thrown when the data store is currently unreachable (issue #228).
 * The system rejects persistence operations with this meaningful error instead of
 * crashing, and automatically resumes once the connection is restored.
 */
public class PersistenceUnavailableException extends RuntimeException {
    public PersistenceUnavailableException(String message) {
        super(message);
    }
}
