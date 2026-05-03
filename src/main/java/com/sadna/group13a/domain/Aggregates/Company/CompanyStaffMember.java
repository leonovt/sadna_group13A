package com.sadna.group13a.domain.Aggregates.Company;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a staff member in the company hierarchy tree.
 */
public class CompanyStaffMember {
    private final String userId;
    private final CompanyRole role;
    private final String appointedByUserId; // null if FOUNDER
    private final Set<CompanyPermission> permissions;

    public CompanyStaffMember(String userId, CompanyRole role, String appointedByUserId, Set<CompanyPermission> permissions) {
        this.userId = userId;
        this.role = role;
        this.appointedByUserId = appointedByUserId;
        this.permissions = permissions == null ? new HashSet<>() : new HashSet<>(permissions);
    }

    public String getUserId() { 
        return userId; 
    }
    
    public CompanyRole getRole() { 
        return role; 
    }
    
    public String getAppointedByUserId() { 
        return appointedByUserId; 
    }
    
    public Set<CompanyPermission> getPermissions() { 
        return Collections.unmodifiableSet(permissions); 
    }
    
    public void setPermissions(Set<CompanyPermission> newPermissions) {
        this.permissions.clear();
        if (newPermissions != null) {
            this.permissions.addAll(newPermissions);
        }
    }
}