package com.sadna.group13a.domain.Events;

import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;

public record StaffNominatedEvent(
    String targetUserId,
    String companyId,
    CompanyRole role,
    String nominatorId
) {}
