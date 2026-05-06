package com.sadna.group13a.domain.Aggregates.Admin;

/**
 * Aggregate root for system administrators.
 * Separate from the User aggregate — an Admin holds a reference to the
 * Member userId that was used when the admin account was created.
 * Auth (login/password) is handled via the Member in UserRepository;
 * governance checks (isAdmin?) are handled via AdminRepository.
 */
public class Admin {

    private final String id;
    private final String userId;

    public Admin(String id, String userId) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Admin id cannot be blank");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId cannot be blank");
        this.id = id;
        this.userId = userId;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
}
