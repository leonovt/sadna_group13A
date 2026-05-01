package com.sadna.group13a.application.DTO;

import java.util.List;
import com.sadna.group13a.domain.shared.CompanyStatus;

/**
 * Data Transfer Object for a Production Company.
 */
public record CompanyDTO(
    String id,
    String name,
    String description,
    CompanyStatus status,
    String founderId,
    List<StaffMemberDTO> staff
) {}
