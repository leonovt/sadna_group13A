package com.sadna.group13a.domain.Aggregates.User;

import com.sadna.group13a.domain.shared.AuthenticationException;
import com.sadna.group13a.domain.shared.UserRole;

/**
 * Administrator — has all Member capabilities plus system management.
 * Can manage users (activate/deactivate), companies, and events.
 * Authorization checks for admin-only operations happen here in the Domain
 * layer.
 */
public class Admin extends User {

    private String passwordHash;

    public Admin(String id, String username, String passwordHash) {
        super(id, username);
        this.passwordHash = passwordHash;
    }

    public Admin(String username, String passwordHash) {
        super(username);
        this.passwordHash = passwordHash;
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

    // ── Permissions ───────────────────────────────────────────────

    @Override
    public boolean canPurchase() {
        return isActive();
    }

    /**
     * Only Admins can manage the system (users, companies, events).
     */
    @Override
    public boolean canManageSystem() {
        return isActive();
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