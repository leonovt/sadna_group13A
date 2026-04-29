package com.sadna.group13a.domain.external;

/**
 * Interface for the external payment gateway.
 * Abstracting this allows us to use different payment providers or mock it for testing.
 */
public interface IPaymentGateway {

    /**
     * Processes a payment for a given order.
     *
     * @param userId         The user making the payment.
     * @param amount         The amount to charge.
     * @param paymentDetails Secure payment information (e.g., credit card token).
     * @return The transaction ID if successful.
     * @throws PaymentException if payment fails.
     */
    String processPayment(String userId, double amount, String paymentDetails);

    /**
     * Processes a refund for a given transaction.
     *
     * @param transactionId The original transaction ID to refund.
     * @param amount        The amount to refund.
     * @return true if refund was successful.
     * @throws PaymentException if refund fails.
     */
    boolean processRefund(String transactionId, double amount);
}
