package com.sadna.group13a.application.dto;

import com.sadna.group13a.domain.company.CompanyStatus;
import java.util.List;

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
