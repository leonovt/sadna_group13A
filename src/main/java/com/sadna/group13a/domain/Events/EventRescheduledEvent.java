package com.sadna.group13a.domain.Events;

import java.time.LocalDateTime;
import java.util.List;

public record EventRescheduledEvent(
    String eventId,
    String eventTitle,
    LocalDateTime newDate,
    List<String> buyerIds
) {}
