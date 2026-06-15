package com.sadna.group13a.domain.Aggregates.User;

import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.Period;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@DiscriminatorValue("MEMBER")
public class Member extends User {

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "hashed_password")
    private String hashedPassword;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_company_roles", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "company_id")
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Map<String, CompanyRole> companyRoles = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_appointed_by", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "company_id")
    @Column(name = "appointer_id")
    private Map<String, String> appointedBy = new HashMap<>();

    protected Member() {}

    public Member(String id, String username, String passwordHash) {
        super(id, username, new MemberState(passwordHash));
        this.hashedPassword = passwordHash;
    }

    public Member(String username, String passwordHash) {
        super(UUID.randomUUID().toString(), username, new MemberState(passwordHash));
        this.hashedPassword = passwordHash;
    }

    @PostLoad
    private void onLoad() {
        setTypeState(new MemberState(hashedPassword, companyRoles, appointedBy));
    }

    // ── Age ───────────────────────────────────────────────────────

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
        incrementVersion();
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public int getAge() {
        if (dateOfBirth == null) return 0;
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    // ── Authentication ────────────────────────────────────────────

    public void setPasswordHash(String passwordHash) {
        this.hashedPassword = passwordHash;
        memberState().setPasswordHash(passwordHash);
        incrementVersion();
    }

    // ── Company role registry ─────────────────────────────────────

    public void addCompanyRole(String companyId, CompanyRole role, String appointedByUserId) {
        memberState().addCompanyRole(companyId, role, appointedByUserId);
        this.companyRoles.put(companyId, role);
        if (appointedByUserId != null) {
            this.appointedBy.put(companyId, appointedByUserId);
        }
        incrementVersion();
    }

    public void removeCompanyRole(String companyId) {
        memberState().removeCompanyRole(companyId);
        this.companyRoles.remove(companyId);
        this.appointedBy.remove(companyId);
        incrementVersion();
    }

    public Map<String, CompanyRole> getCompanyRoles() {
        return Collections.unmodifiableMap(memberState().getCompanyRoles());
    }

    public CompanyRole getRoleInCompany(String companyId) {
        return memberState().getRoleInCompany(companyId);
    }

    public String getAppointedByInCompany(String companyId) {
        return memberState().getAppointedByInCompany(companyId);
    }

    // ── Internal ──────────────────────────────────────────────────

    private MemberState memberState() {
        return (MemberState) getTypeState();
    }
}
