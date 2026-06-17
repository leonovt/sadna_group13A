package com.sadna.group13a.domain.Aggregates.User;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_type", discriminatorType = DiscriminatorType.STRING)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "userClass")
@JsonSubTypes({
        @JsonSubTypes.Type(Guest.class),
        @JsonSubTypes.Type(Member.class)
})
public class User {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserState state;

    @Transient
    private UserTypeState typeState;

    @Column(name = "active_order_id")
    private String activeOrderId;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    @Version
    private int version;

    protected User() {}

    protected User(String id, String username, UserTypeState initialState) {
        this.id = id;
        this.username = username;
        this.state = UserState.ACTIVE;
        this.typeState = initialState;
        this.activeOrderId = null;
    }

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
        this.suspendedAt = null;
        this.suspendedUntil = null;
        incrementVersion();
    }

    public void deactivate() {
        this.state = UserState.INACTIVE;
        incrementVersion();
    }

    // ── Suspension ────────────────────────────────────────────────

    public void suspend(LocalDateTime suspendedAt, LocalDateTime suspendedUntil) {
        this.state = UserState.INACTIVE;
        this.suspendedAt = suspendedAt;
        this.suspendedUntil = suspendedUntil;
        incrementVersion();
    }

    public boolean isSuspended() {
        return state == UserState.INACTIVE && suspendedAt != null;
    }

    public LocalDateTime getSuspendedAt() {
        return suspendedAt;
    }

    public LocalDateTime getSuspendedUntil() {
        return suspendedUntil;
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
