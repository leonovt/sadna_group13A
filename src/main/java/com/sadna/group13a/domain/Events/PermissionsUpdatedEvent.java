package com.sadna.group13a.domain.Events;

public record PermissionsUpdatedEvent(
    String targetUserId,
    String companyId
) {}
