package com.sadna.group13a.application.DTO;

import com.sadna.group13a.domain.shared.CompanyPermission;
import com.sadna.group13a.domain.shared.CompanyRole;

import java.util.Set;

/**
 * Data Transfer Object for a Company Staff Member.
 */
public record StaffMemberDTO(
    String userId,
    CompanyRole role,
    Set<CompanyPermission> permissions
) {}
