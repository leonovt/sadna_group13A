package com.sadna.group13a.domain.shared;

/**
 * Thrown when the external payment gateway fails in a way that escaped its own
 * defensive handling (e.g. an unexpected exception rather than a Result failure).
 * Caught within the same checkout use case that throws it — never crosses the
 * application service boundary — so callers still see a {@code Result} failure.
 */
public class PaymentFailedException extends DomainException {

    public PaymentFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
