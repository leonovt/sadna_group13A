package com.sadna.group13a.application.dto;

import com.sadna.group13a.domain.company.CompanyPermission;
import com.sadna.group13a.domain.company.CompanyRole;
import java.util.Set;

/**
 * Data Transfer Object for a Company Staff Member.
 */
public record StaffMemberDTO(
    String userId,
    CompanyRole role,
    Set<CompanyPermission> permissions
) {}
