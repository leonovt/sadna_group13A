package com.sadna.group13a.domain.shared;

/**
 * Thrown when a user lacks the required role/permission
 * to perform a domain operation.
 * Authorization checks happen in the Domain layer per requirements.
 */
public class PermissionDeniedException extends DomainException {

    public PermissionDeniedException(String action) {
        super("Permission denied for action: " + action);
    }

    public PermissionDeniedException(String userId, String action) {
        super("User " + userId + " does not have permission to: " + action);
    }
}
