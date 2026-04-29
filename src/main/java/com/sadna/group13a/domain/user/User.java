package com.sadna.group13a.domain.user;

import com.sadna.group13a.domain.shared.UserRole;
import com.sadna.group13a.domain.shared.UserState;

import java.util.UUID;

/**
 * Aggregate Root for the User aggregate.
 * From UML: User (Root) — State: Active/Inactive, Pointer: activeOrderId.
 *
 * Guest, Member, and Admin are concrete subclasses, each carrying
 * their own domain logic (permissions, session management, etc.).
 */
public abstract class User {

    private final String id;
    private String username;
    private UserState state;
    private String activeOrderId;  // pointer to current cart (from UML)

    protected User(String id, String username) {
        this.id = id;
        this.username = username;
        this.state = UserState.ACTIVE;
        this.activeOrderId = null;
    }

    protected User(String username) {
        this(UUID.randomUUID().toString(), username);
    }

    // ── Identity & State ──────────────────────────────────────────

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UserState getState() {
        return state;
    }

    public boolean isActive() {
        return state == UserState.ACTIVE;
    }

    public void activate() {
        this.state = UserState.ACTIVE;
    }

    public void deactivate() {
        this.state = UserState.INACTIVE;
    }

    // ── Active Order Pointer ──────────────────────────────────────

    public String getActiveOrderId() {
        return activeOrderId;
    }

    public void setActiveOrderId(String activeOrderId) {
        this.activeOrderId = activeOrderId;
    }

    public boolean hasActiveOrder() {
        return activeOrderId != null;
    }

    // ── Role (subclass identity) ──────────────────────────────────

    /**
     * Returns the role of this user. Each subclass provides its own value.
     */
    public abstract UserRole getRole();

    // ── Domain-level authorization ────────────────────────────────

    /**
     * Whether this user can purchase tickets.
     * Overridden by subclasses that support purchasing.
     */
    public boolean canPurchase() {
        return false;
    }

    /**
     * Whether this user can manage companies and events.
     * Only Admin overrides this to return true.
     */
    public boolean canManageSystem() {
        return false;
    }

    /**
     * Whether this user holds a valid authenticated session.
     * Guests are never authenticated.
     */
    public abstract boolean isAuthenticated();
}
