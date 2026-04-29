package com.sadna.group13a.domain.user;

import com.sadna.group13a.domain.shared.AuthenticationException;
import com.sadna.group13a.domain.shared.UserRole;

/**
 * Registered member — authenticated user who can purchase tickets.
 * Owns a SessionToken for JWT-based authentication.
 * Has order history and can manage their own cart.
 */
public class Member extends User {

    private String passwordHash;
    private SessionToken sessionToken;  // composition from UML

    public Member(String id, String username, String passwordHash) {
        super(id, username);
        this.passwordHash = passwordHash;
        this.sessionToken = null;
    }

    public Member(String username, String passwordHash) {
        super(username);
        this.passwordHash = passwordHash;
        this.sessionToken = null;
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

    public SessionToken getSessionToken() {
        return sessionToken;
    }

    /**
     * Assigns a new session token upon successful login.
     */
    public void login(SessionToken token) {
        if (!isActive()) {
            throw new AuthenticationException("Cannot login: account is inactive");
        }
        this.sessionToken = token;
    }

    /**
     * Clears the session token on logout.
     */
    public void logout() {
        this.sessionToken = null;
    }

    @Override
    public boolean isAuthenticated() {
        return sessionToken != null && sessionToken.isValid();
    }

    // ── Permissions ───────────────────────────────────────────────

    /**
     * Members can purchase tickets when authenticated and active.
     */
    @Override
    public boolean canPurchase() {
        return isActive() && isAuthenticated();
    }
}
