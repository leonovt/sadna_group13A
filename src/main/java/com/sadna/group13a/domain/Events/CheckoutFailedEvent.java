package com.sadna.group13a.domain.Events;

public record CheckoutFailedEvent(
    String userId,
    String reason
) {}
