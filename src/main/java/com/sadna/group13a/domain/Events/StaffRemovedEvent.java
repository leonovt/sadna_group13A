package com.sadna.group13a.domain.Events;

import java.util.List;

public record StaffRemovedEvent(
    List<String> removedUserIds,
    String companyId,
    String removedByUserId
) {}
