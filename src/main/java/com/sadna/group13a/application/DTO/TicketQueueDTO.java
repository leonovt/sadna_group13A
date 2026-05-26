package com.sadna.group13a.application.DTO;

public record TicketQueueDTO(
        String eventId,
        int maxConcurrentUsers,
        int activeCount,
        int waitingCount
) {}
