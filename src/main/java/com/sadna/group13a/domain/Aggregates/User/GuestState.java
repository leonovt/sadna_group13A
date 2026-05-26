package com.sadna.group13a.domain.Aggregates.User;

class GuestState implements UserTypeState {

    @Override public UserRole getRole() { return UserRole.GUEST; }
    @Override public String getHashedPassword() { return null; }
    @Override public boolean canPurchase(boolean isActive) { return false; }
    @Override public boolean canManageSystem() { return false; }
}
