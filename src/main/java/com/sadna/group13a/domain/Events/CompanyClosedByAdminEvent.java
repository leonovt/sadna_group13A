package com.sadna.group13a.domain.Events;

public record CompanyClosedByAdminEvent(
    String companyId,
    String adminId
) {}
