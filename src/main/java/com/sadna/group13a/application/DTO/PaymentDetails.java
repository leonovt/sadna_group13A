package com.sadna.group13a.application.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Structured credit-card details collected at checkout (V3 issue #225).
 *
 * <p>Carried as a JSON string through the existing {@code IPaymentGateway.processPayment(double,
 * String)} parameter: the checkout UI serializes this record, and {@code WsepPaymentGateway}
 * deserializes it to populate the WSEP {@code pay} request. The in-memory stub ignores it.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentDetails(
        String cardNumber,
        String month,
        String year,
        String holder,
        String cvv,
        String id,
        String currency
) {
}
