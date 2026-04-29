package com.sadna.group13a.domain.external;

/**
 * Interface for generating and verifying digital tickets.
 */
public interface ITicketSupplier {

    /**
     * Generates a digital ticket (barcode/QR data) for a specific seat in an order.
     *
     * @param orderId The completed order ID.
     * @param eventId The event the ticket is for.
     * @param seatId  The specific seat (or standing zone marker).
     * @return A unique barcode or ticket ID.
     */
    String generateTicket(String orderId, String eventId, String seatId);
    
    /**
     * Revokes a ticket if an order is cancelled/refunded.
     *
     * @param ticketBarcode The barcode to revoke.
     * @return true if successfully revoked.
     */
    boolean revokeTicket(String ticketBarcode);
}
