package com.sadna.group13a.domain.user;

import com.sadna.group13a.domain.shared.UserRole;

/**
 * Guest user — unauthenticated visitor.
 * Can browse and search events but cannot purchase or manage anything.
 * A Guest becomes a Member upon registration (handled by AuthAppService).
 */
public class Guest extends User {

    public Guest(String id, String username) {
        super(id, username);
    }

    public Guest(String username) {
        super(username);
    }

    @Override
    public UserRole getRole() {
        return UserRole.GUEST;
    }

    @Override
    public boolean isAuthenticated() {
        return false;  // Guests never have a session
    }

    // Guests cannot purchase or manage — defaults from User (false) apply
}
