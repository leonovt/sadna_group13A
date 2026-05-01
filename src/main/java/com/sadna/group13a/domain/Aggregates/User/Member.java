package com.sadna.group13a.domain.Aggregates.User;


import com.sadna.group13a.domain.shared.UserRole;

/**
 * Registered member — authenticated user who can purchase tickets.
 * Owns a SessionToken for JWT-based authentication.
 * Has order history and can manage their own cart.
 */
public class Member extends User {

    private String passwordHash;

    public Member(String id, String username, String passwordHash) {
        super(id, username);
        this.passwordHash = passwordHash;
    }

    public Member(String username, String passwordHash) {
        super(username);
        this.passwordHash = passwordHash;
    }

    // ── Role ──────────────────────────────────────────────────────

    @Override
    public UserRole getRole() {
        return UserRole.MEMBER;
    }

    // ── Authentication & Session ──────────────────────────────────

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    // ── Permissions ───────────────────────────────────────────────

    /**
     * Members can purchase tickets when authenticated and active.
     */
    @Override
    public boolean canPurchase() {
        return isActive();
    }
}