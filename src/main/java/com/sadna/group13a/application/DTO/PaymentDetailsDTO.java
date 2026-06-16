package com.sadna.group13a.application.DTO;

/**
 * Card details collected at checkout and serialized to JSON before being
 * passed through the service layer to {@code RealPaymentGateway}.
 *
 * <p>Field names match the WSEP external-system API parameters.
 */
public record PaymentDetailsDTO(
        String cardNumber,
        String month,
        String year,
        String holder,
        String cvv,
        String holderId,
        String currency
) {}
