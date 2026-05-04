package com.sadna.group13a.application.Interfaces;

import com.sadna.group13a.application.Result;

public interface IPaymentGateway 
{
    /**
     * Checks if the external payment service is currently reachable and active.
     * Required for Platform Initialization (UC 1.1).
     */
    boolean isConnected();

    /**
     * Charges the customer for their ticket order.
     *
     * @param amount The total amount to charge.
     * @param paymentDetails The credit card or token details.
     * @return A Result containing the unique Transaction ID if successful, or a failure message.
     */
    Result<String> processPayment(double amount, String paymentDetails);

    /**
     * Refunds a previously successful transaction.
     * Required in case ticket issuance fails after payment, or if an event is canceled.
     *
     * @param transactionId The unique ID of the transaction to refund.
     * @return A Result indicating success or failure of the refund.
     */
    Result<Void> refundPayment(String transactionId);
}
