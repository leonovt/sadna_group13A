package com.sadna.group13a.application.DTO;

import java.util.Set;

import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;

/**
 * Data Transfer Object for a Company Staff Member.
 */
public record StaffMemberDTO(
    String userId,
    CompanyRole role,
    Set<CompanyPermission> permissions
) {}
