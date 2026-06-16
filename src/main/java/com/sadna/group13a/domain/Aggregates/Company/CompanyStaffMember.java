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
 * Represents a staff member in the company hierarchy tree.
 */
@Entity
@Table(name = "company_staff")
public class CompanyStaffMember {

    /** Surrogate primary key — the business key is userId. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "member_id")
    private String memberId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private CompanyRole role;

    @Column(name = "appointed_by_user_id")
    private String appointedByUserId; // null if FOUNDER

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "staff_permissions", joinColumns = @JoinColumn(name = "member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    private Set<CompanyPermission> permissions;

    /** Required by JPA. Do not use in business code. */
    protected CompanyStaffMember() {}

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

    public void setAppointedByUserId(String newAppointedByUserId) {
        this.appointedByUserId = newAppointedByUserId;
    }

    public void setPermissions(Set<CompanyPermission> newPermissions) {
        this.permissions.clear();
        if (newPermissions != null) {
            this.permissions.addAll(newPermissions);
        }
    }
}
