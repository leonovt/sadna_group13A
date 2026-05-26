package com.sadna.group13a.domain.Aggregates.User;

interface UserTypeState {
    UserRole getRole();
    String getHashedPassword();
    boolean canPurchase(boolean isActive);
    boolean canManageSystem();
}
