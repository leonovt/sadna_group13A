package com.sadna.group13a.domain.Events;

/**
 * Published when a buyer is automatically refunded — e.g. after an event they
 * purchased tickets for is cancelled. Drives a real-time / deferred notification
 * to the affected customer (Observer pattern).
 */
public record RefundIssuedEvent(
    String userId,
    String receiptId,
    double amount,
    String eventTitle
) {}
