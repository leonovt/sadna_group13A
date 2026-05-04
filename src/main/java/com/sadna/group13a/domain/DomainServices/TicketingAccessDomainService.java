package com.sadna.group13a.domain.DomainServices;

import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Raffle.AuthorizationCode;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.shared.PermissionDeniedException;

/**
 * Domain Service — pure Java, no Spring annotations.
 * Acts as the gatekeeper: verifies that a user is legally allowed to start a checkout
 * for a given event based on its sale mode (REGULAR, QUEUE, or RAFFLE).
 */
public class TicketingAccessDomainService {

    /**
     * Validates that the given user has the right to purchase tickets for the event right now.
     *
     * @param event     the event being purchased
     * @param userId    the buyer's ID
     * @param queue     the event's TicketQueue — required for QUEUE events, ignored otherwise
     * @param authCode  the raffle winner's AuthorizationCode — required for RAFFLE events, ignored otherwise
     * @throws PermissionDeniedException if access is denied under the event's sale mode
     */
    public void validateAccess(
            Event event,
            String userId,
            TicketQueue queue,
            AuthorizationCode authCode) {

        switch (event.getSaleMode()) {
            case REGULAR -> {
                // No restriction — all authenticated users may proceed
            }
            case QUEUE -> {
                if (queue == null) {
                    throw new PermissionDeniedException("purchase tickets for a queue-based event with no queue configured");
                }
                if (!queue.isUserActive(userId)) {
                    throw new PermissionDeniedException(userId, "purchase tickets — not in the active queue window");
                }
            }
            case RAFFLE -> {
                if (authCode == null) {
                    throw new PermissionDeniedException(userId, "purchase tickets — a raffle authorization code is required");
                }
                if (!authCode.isValidFor(userId, event.getId())) {
                    throw new PermissionDeniedException(userId, "purchase tickets — authorization code is invalid or has expired");
                }
            }
        }
    }
}
