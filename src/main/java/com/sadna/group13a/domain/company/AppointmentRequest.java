package com.sadna.group13a.domain.company;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks a pending nomination for a role.
 */
public class AppointmentRequest {
    private final String nomineeId;
    private final String appointerId;
    private final CompanyRole proposedRole;
    private final Set<CompanyPermission> proposedPermissions;

    public AppointmentRequest(String nomineeId, String appointerId, CompanyRole proposedRole, Set<CompanyPermission> proposedPermissions) {
        this.nomineeId = nomineeId;
        this.appointerId = appointerId;
        this.proposedRole = proposedRole;
        this.proposedPermissions = proposedPermissions == null ? new HashSet<>() : new HashSet<>(proposedPermissions);
    }

    public String getNomineeId() { 
        return nomineeId; 
    }
    
    public String getAppointerId() { 
        return appointerId; 
    }
    
    public CompanyRole getProposedRole() { 
        return proposedRole; 
    }
    
    public Set<CompanyPermission> getProposedPermissions() { 
        return Collections.unmodifiableSet(proposedPermissions); 
    }
}
