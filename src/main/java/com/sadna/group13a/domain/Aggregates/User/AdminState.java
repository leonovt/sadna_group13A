package com.sadna.group13a.domain.Aggregates.User;

class AdminState implements UserTypeState {

    private final String passwordHash;

    AdminState(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @Override public UserRole getRole() { return UserRole.ADMIN; }
    @Override public String getHashedPassword() { return passwordHash; }
    @Override public boolean canPurchase(boolean isActive) { return isActive; }
    @Override public boolean canManageSystem() { return true; }
}
