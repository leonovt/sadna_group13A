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
     * Issues secure digital tickets for a completed order, one per request.
     * Each request carries the event/zone (and seat, for assigned-seating zones) so
     * that the external system can issue the correct ticket type.
     *
     * <p>Issuance is atomic: if any single ticket fails to issue (the external system
     * returns {@code -1}), the implementation cancels every ticket it has already issued
     * for this call and returns a failure {@link Result}.
     *
     * @param customerId The id of the purchasing customer.
     * @param requests   One entry per ticket to issue.
     * @return A Result containing the generated ticket codes, aligned by index with
     *         {@code requests}, or a failure if any ticket could not be issued.
     */
    Result<List<String>> issueTickets(String customerId, List<TicketIssueRequest> requests);

    /**
     * Cancels previously issued tickets.
     * Used when an event is canceled or a purchase is rolled back / refunded.
     *
     * @param ticketCodes The specific ticket codes/ids to invalidate.
     * @return A Result indicating success or failure.
     */
    Result<Void> cancelTickets(List<String> ticketCodes);
}
