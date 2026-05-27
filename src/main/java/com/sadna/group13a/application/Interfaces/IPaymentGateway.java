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

    /**
     * Refunds part of a previously successful transaction. Used when only some of the
     * items on a multi-event receipt must be reimbursed — e.g. one of several events on
     * the same transaction is cancelled.
     *
     * @param transactionId The unique ID of the transaction to partially refund.
     * @param amount        The amount to refund (must be &gt; 0 and &le; the original charge).
     * @return A Result indicating success or failure of the partial refund.
     */
    Result<Void> refundPartial(String transactionId, double amount);
}
