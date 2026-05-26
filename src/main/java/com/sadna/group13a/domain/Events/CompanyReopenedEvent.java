package com.sadna.group13a.domain.Events;

import java.util.List;

public record CompanyReopenedEvent(
    String companyId,
    String actingUserId,
    List<String> staffUserIds
) {}
