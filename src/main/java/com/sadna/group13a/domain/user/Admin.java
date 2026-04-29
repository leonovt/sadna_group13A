package com.sadna.group13a.domain.user;

import com.sadna.group13a.domain.shared.AuthenticationException;
import com.sadna.group13a.domain.shared.UserRole;

/**
 * Administrator — has all Member capabilities plus system management.
 * Can manage users (activate/deactivate), companies, and events.
 * Authorization checks for admin-only operations happen here in the Domain layer.
 */
public class Admin extends User {

    private String passwordHash;
    private SessionToken sessionToken;  // composition from UML

    public Admin(String id, String username, String passwordHash) {
        super(id, username);
        this.passwordHash = passwordHash;
        this.sessionToken = null;
    }

    public Admin(String username, String passwordHash) {
        super(username);
        this.passwordHash = passwordHash;
        this.sessionToken = null;
    }

    // ── Role ──────────────────────────────────────────────────────

    @Override
    public UserRole getRole() {
        return UserRole.ADMIN;
    }

    // ── Authentication & Session ──────────────────────────────────

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public SessionToken getSessionToken() {
        return sessionToken;
    }

    public void login(SessionToken token) {
        if (!isActive()) {
            throw new AuthenticationException("Cannot login: account is inactive");
        }
        this.sessionToken = token;
    }

    public void logout() {
        this.sessionToken = null;
    }

    @Override
    public boolean isAuthenticated() {
        return sessionToken != null && sessionToken.isValid();
    }

    // ── Permissions ───────────────────────────────────────────────

    @Override
    public boolean canPurchase() {
        return isActive() && isAuthenticated();
    }

    /**
     * Only Admins can manage the system (users, companies, events).
     */
    @Override
    public boolean canManageSystem() {
        return isActive() && isAuthenticated();
    }

    // ── Admin-specific domain operations ──────────────────────────

    /**
     * Admin deactivates a target user's account.
     * The target user's token will fail on next validation.
     */
    public void deactivateUser(User target) {
        if (!canManageSystem()) {
            throw new AuthenticationException("Admin must be authenticated to manage users");
        }
        target.deactivate();
    }

    /**
     * Admin activates a previously deactivated user account.
     */
    public void activateUser(User target) {
        if (!canManageSystem()) {
            throw new AuthenticationException("Admin must be authenticated to manage users");
        }
        target.activate();
    }
}
