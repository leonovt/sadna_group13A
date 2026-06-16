package com.sadna.group13a.infrastructure.init;

/**
 * Thrown when the system cannot be initialized (issue #230): an invalid configuration,
 * an unreadable/malformed initial-state file, or a failed/illegal start-up operation.
 * Propagating this during start-up makes the application refuse to start.
 */
public class SystemInitializationException extends RuntimeException {

    public SystemInitializationException(String message) {
        super(message);
    }

    public SystemInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
