package com.sadna.group13a.domain.Events;

public record CartExpiredEvent(
    String userId,
    String cartId
) {}
