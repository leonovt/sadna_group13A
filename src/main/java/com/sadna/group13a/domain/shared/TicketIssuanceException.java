package com.sadna.group13a.domain.shared;

/**
 * Thrown when the external ticket-issuance system fails in a way that escaped its own
 * defensive handling (e.g. an unexpected exception rather than a Result failure).
 * Caught within the same checkout use case that throws it — never crosses the
 * application service boundary — so callers still see a {@code Result} failure.
 */
public class TicketIssuanceException extends DomainException {

    public TicketIssuanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
