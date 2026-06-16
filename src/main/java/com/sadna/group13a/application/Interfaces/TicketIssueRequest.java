package com.sadna.group13a.application.Interfaces;

/**
 * Describes a single ticket to be issued by the external ticket-issuance system.
 * One request corresponds to exactly one ticket (one seat, or one standing spot),
 * so the supplier returns exactly one ticket code per request.
 *
 * <ul>
 *   <li>{@code seating == true}  — assigned-seating zone; {@code row}/{@code seat} are sent
 *       to the external system as a {@code seats} array.</li>
 *   <li>{@code seating == false} — standing zone; a {@code quantity} of 1 is sent instead.</li>
 * </ul>
 */
public record TicketIssueRequest(
        String eventId,
        String zone,
        boolean seating,
        int row,
        int seat
) {}
