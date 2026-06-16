package com.sadna.group13a.infrastructure.initstate;

/**
 * Thrown when the initial-state file cannot be parsed or one of its operations fails.
 *
 * <p>Per V3 issue #224 initialization is all-or-nothing: if this propagates out of the
 * startup runner, the Spring context fails and the application does not start.</p>
 */
public class InitialStateException extends RuntimeException {

    public InitialStateException(String message) {
        super(message);
    }

    public InitialStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
