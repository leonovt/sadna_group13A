package com.sadna.group13a.domain.Aggregates.User;

/**
 * User roles in the system.
 * Implemented as an enum rather than class hierarchy per V0 requirement
 * ("classes without hierarchy").
 */
public enum UserRole {
    GUEST,
    MEMBER,
    ADMIN
}
