package com.sadna.group13a.domain.Events;

import java.util.List;

public record EventSoldOutEvent(
    String eventId,
    String eventTitle,
    List<String> staffUserIds
) {}
