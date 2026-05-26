package com.sadna.group13a.domain.Events;

import java.util.List;

public record EventCancelledEvent(
    String eventId,
    String eventTitle,
    List<String> buyerIds
) {}
