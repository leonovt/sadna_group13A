package com.sadna.group13a.domain.Aggregates.User;

import java.util.UUID;


public abstract class User {

    private final String id;
    private String username;
    private UserState state;
    private String activeOrderId; // pointer to current cart (from UML)

    private int version = 0;

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

    public void setUsername(String username)
    {
        this.username = username;
        incrementVersion();
    }

    public UserState getState() {
        return state;
    }

    public boolean isActive() {
        return state == UserState.ACTIVE;
    }

    public void activate() {
        this.state = UserState.ACTIVE;
        incrementVersion();
    }

    public void deactivate() {
        this.state = UserState.INACTIVE;
        incrementVersion();
    }

    // ── Active Order Pointer ──────────────────────────────────────

    public String getActiveOrderId() {
        return activeOrderId;
    }

    public void setActiveOrderId(String activeOrderId) {
        this.activeOrderId = activeOrderId;
        incrementVersion();
    }

    public boolean hasActiveOrder() {
        return activeOrderId != null;
    }

    // ── Role (subclass identity) ──────────────────────────────────

    /**
     * Returns the role of this user. Each subclass provides its own value.
     */
    public abstract UserRole getRole();

    /**
     * Returns the stored password hash. Guest users have no password and return null.
     */
    public abstract String getHashedPassword();

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

    public int getVersion()
    {
        return version;
    }

    protected void incrementVersion() {
        this.version++;
    }
}
