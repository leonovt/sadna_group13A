package com.sadna.group13a.domain.Aggregates.User;

import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registered member — authenticated user who can purchase tickets and hold company roles.
 * Owns a password hash for JWT-based authentication.
 * Tracks company affiliations (role + appointer) so the user knows its own roles
 * without always querying through ProductionCompany.
 */
public class Member extends User {

    private String passwordHash;

    // companyId → role the member holds in that company
    private final Map<String, CompanyRole> companyRoles = new ConcurrentHashMap<>();
    // companyId → userId of whoever appointed this member (null for founders)
    private final Map<String, String> appointedBy = new ConcurrentHashMap<>();

    public Member(String id, String username, String passwordHash) {
        super(id, username);
        this.passwordHash = passwordHash;
    }

    public Member(String username, String passwordHash) {
        super(username);
        this.passwordHash = passwordHash;
    }

    // ── Role ──────────────────────────────────────────────────────

    @Override
    public UserRole getRole() {
        return UserRole.MEMBER;
    }

    // ── Authentication ────────────────────────────────────────────

    @Override
    public String getHashedPassword() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        incrementVersion();
    }

    // ── Permissions ───────────────────────────────────────────────

    @Override
    public boolean canPurchase() {
        return isActive();
    }

    // ── Company role registry ─────────────────────────────────────

    public void addCompanyRole(String companyId, CompanyRole role, String appointedByUserId) {
        companyRoles.put(companyId, role);
        if (appointedByUserId != null) {
            appointedBy.put(companyId, appointedByUserId);
        }
        incrementVersion();
    }

    public void removeCompanyRole(String companyId) {
        companyRoles.remove(companyId);
        appointedBy.remove(companyId);
        incrementVersion();
    }

    public Map<String, CompanyRole> getCompanyRoles() {
        return Collections.unmodifiableMap(companyRoles);
    }

    public CompanyRole getRoleInCompany(String companyId) {
        return companyRoles.get(companyId);
    }

    public String getAppointedByInCompany(String companyId) {
        return appointedBy.get(companyId);
    }
}
