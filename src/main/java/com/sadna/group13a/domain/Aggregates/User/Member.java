package com.sadna.group13a.domain.Aggregates.User;

import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class Member extends User {

    public Member(String id, String username, String passwordHash) {
        super(id, username, new MemberState(passwordHash));
    }

    public Member(String username, String passwordHash) {
        super(UUID.randomUUID().toString(), username, new MemberState(passwordHash));
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
