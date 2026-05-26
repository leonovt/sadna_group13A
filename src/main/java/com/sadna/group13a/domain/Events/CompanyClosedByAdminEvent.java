package com.sadna.group13a.domain.Events;

import java.util.List;

public record CompanyClosedByAdminEvent(
    String companyId,
    String adminId,
    List<String> staffUserIds
) {}
