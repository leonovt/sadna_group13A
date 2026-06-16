package com.sadna.group13a.domain.Aggregates.Company;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks a pending nomination for a role.
 */
@Entity
@Table(name = "appointment_requests")
public class AppointmentRequest {

    /** Surrogate primary key — the business key is nomineeId. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "request_id")
    private String requestId;

    @Column(name = "nominee_id", nullable = false)
    private String nomineeId;

    @Column(name = "appointer_id", nullable = false)
    private String appointerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_role", nullable = false)
    private CompanyRole proposedRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "appointment_permissions", joinColumns = @JoinColumn(name = "request_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    private Set<CompanyPermission> proposedPermissions;

    /** Required by JPA. Do not use in business code. */
    protected AppointmentRequest() {}

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
