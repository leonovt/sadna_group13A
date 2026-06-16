package com.sadna.group13a.domain.Aggregates.User;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;

import java.time.LocalDate;
import java.time.Period;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class Member extends User {

    private LocalDate dateOfBirth;

    @JsonCreator
    public Member(@JsonProperty("id") String id, @JsonProperty("username") String username,
                  @JsonProperty("passwordHash") String passwordHash) {
        super(id, username, new MemberState(passwordHash));
    }

    public Member(String username, String passwordHash) {
        super(UUID.randomUUID().toString(), username, new MemberState(passwordHash));
    }

    // ── Age ───────────────────────────────────────────────────────

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
        incrementVersion();
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    /** Returns the member's current age in years, or 0 if date of birth is not set. */
    public int getAge() {
        if (dateOfBirth == null) return 0;
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    // ── Authentication ────────────────────────────────────────────

    public void setPasswordHash(String passwordHash) {
        memberState().setPasswordHash(passwordHash);
        incrementVersion();
    }

    // ── Company role registry ─────────────────────────────────────

    public void addCompanyRole(String companyId, CompanyRole role, String appointedByUserId) {
        memberState().addCompanyRole(companyId, role, appointedByUserId);
        incrementVersion();
    }

    public void removeCompanyRole(String companyId) {
        memberState().removeCompanyRole(companyId);
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
