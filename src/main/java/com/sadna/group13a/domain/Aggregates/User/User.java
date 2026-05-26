package com.sadna.group13a.domain.Aggregates.User;

import java.util.UUID;


public class User {

    private final String id;
    private String username;
    private UserState state;
    private UserTypeState typeState;
    private String activeOrderId;

    private int version = 0;

    protected User(String id, String username, UserTypeState initialState) {
        this.id = id;
        this.username = username;
        this.state = UserState.ACTIVE;
        this.typeState = initialState;
        this.activeOrderId = null;
    }

    // Convenience constructors — default to Guest
    protected User(String id, String username) {
        this(id, username, new GuestState());
    }

    protected User(String username) {
        this(UUID.randomUUID().toString(), username, new GuestState());
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

    // ── Role & Type (delegated to current state) ──────────────────

    public UserRole getRole() {
        return typeState.getRole();
    }

    public String getHashedPassword() {
        return typeState.getHashedPassword();
    }

    public boolean canPurchase() {
        return typeState.canPurchase(isActive());
    }

    public boolean canManageSystem() {
        return typeState.canManageSystem();
    }

    // ── Version ───────────────────────────────────────────────────

    public int getVersion() {
        return version;
    }

    protected void incrementVersion() {
        this.version++;
    }

    // ── Type state access (for subclasses) ────────────────────────

    protected UserTypeState getTypeState() {
        return typeState;
    }

    protected void setTypeState(UserTypeState newState) {
        this.typeState = newState;
        incrementVersion();
    }
}
