package com.sadna.group13a.application.Interfaces;

import com.sadna.group13a.application.Result;
import java.util.List;

public interface ITicketSupplier 
{
    /**
     * Checks if the external ticket supply service is currently reachable and active.
     * Required for Platform Initialization (UC 1.1).
     */
    boolean isConnected();

    /**
     * Issues secure digital tickets for a completed order.
     *
     * @param orderId The internal ID of the confirmed order.
     * @param quantity The number of tickets to issue.
     * @return A Result containing a list of generated secure barcodes/ticket codes.
     */
    Result<List<String>> issueTickets(String orderId, int quantity);

    /**
     * Cancels previously issued tickets.
     * Used when an event is canceled or a user is refunded.
     *
     * @param ticketCodes The specific barcodes/codes to invalidate.
     * @return A Result indicating success or failure.
     */
    Result<Void> cancelTickets(List<String> ticketCodes);
}
