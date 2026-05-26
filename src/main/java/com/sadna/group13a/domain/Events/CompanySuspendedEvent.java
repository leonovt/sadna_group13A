package com.sadna.group13a.domain.Events;

import java.util.List;

public record CompanySuspendedEvent(
    String companyId,
    String actingUserId,
    List<String> staffUserIds
) {}
