package com.sadna.group13a.domain.Aggregates.User;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

class AdminState implements UserTypeState {

    private final String passwordHash;

    @JsonCreator
    AdminState(@JsonProperty("passwordHash") String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @Override public UserRole getRole() { return UserRole.ADMIN; }
    @Override public String getHashedPassword() { return passwordHash; }
    @Override public boolean canPurchase(boolean isActive) { return isActive; }
    @Override public boolean canManageSystem() { return true; }
}
