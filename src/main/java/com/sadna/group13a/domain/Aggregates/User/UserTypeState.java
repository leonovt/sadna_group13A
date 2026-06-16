package com.sadna.group13a.domain.Aggregates.User;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "stateClass")
@JsonSubTypes({
        @JsonSubTypes.Type(GuestState.class),
        @JsonSubTypes.Type(MemberState.class),
        @JsonSubTypes.Type(AdminState.class)
})
interface UserTypeState {
    UserRole getRole();
    String getHashedPassword();
    boolean canPurchase(boolean isActive);
    boolean canManageSystem();
}
