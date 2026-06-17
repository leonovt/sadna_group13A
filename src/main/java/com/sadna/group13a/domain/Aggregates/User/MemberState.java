package com.sadna.group13a.domain.Aggregates.User;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class MemberState implements UserTypeState {

    private String passwordHash;
    private final Map<String, CompanyRole> companyRoles = new ConcurrentHashMap<>();
    private final Map<String, String> appointedBy = new ConcurrentHashMap<>();

    @JsonCreator
    MemberState(@JsonProperty("passwordHash") String passwordHash) {
        this.passwordHash = passwordHash;
    }

    MemberState(String passwordHash, Map<String, CompanyRole> roles, Map<String, String> appointed) {
        this.passwordHash = passwordHash;
        this.companyRoles.putAll(roles);
        this.appointedBy.putAll(appointed);
    }

    @Override public UserRole getRole() { return UserRole.MEMBER; }
    @Override public String getHashedPassword() { return passwordHash; }
    @Override public boolean canPurchase(boolean isActive) { return isActive; }
    @Override public boolean canManageSystem() { return false; }

    void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    void addCompanyRole(String companyId, CompanyRole role, String appointedByUserId) {
        companyRoles.put(companyId, role);
        if (appointedByUserId != null) {
            appointedBy.put(companyId, appointedByUserId);
        }
    }

    void removeCompanyRole(String companyId) {
        companyRoles.remove(companyId);
        appointedBy.remove(companyId);
    }

    Map<String, CompanyRole> getCompanyRoles() {
        return Collections.unmodifiableMap(companyRoles);
    }

    CompanyRole getRoleInCompany(String companyId) {
        return companyRoles.get(companyId);
    }

    String getAppointedByInCompany(String companyId) {
        return appointedBy.get(companyId);
    }
}
