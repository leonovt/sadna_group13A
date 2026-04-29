package com.sadna.group13a.domain.external;

/**
 * Custom exception representing payment processing failures.
 */
public class PaymentException extends RuntimeException {
    public PaymentException(String message) {
        super(message);
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
