package com.sadna.group13a.domain.Events;

public record OrderCompletedEvent(
    String receiptId,
    String userId,
    double totalPaid
) {}
