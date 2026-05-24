package com.sadna.group13a.domain.DomainServices;

import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Raffle.AuthorizationCode;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.shared.PermissionDeniedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Domain Service — pure Java, no Spring annotations.
 * Acts as the gatekeeper: verifies that a purchase can legally proceed for a given
 * event and user. Covers two orthogonal concerns:
 *   1. Event-level availability — is this event currently open for sale?
 *   2. User-level authorization — is this specific user allowed to buy under the sale mode?
 */
public class TicketingAccessDomainService {

    private static final Logger logger = LoggerFactory.getLogger(TicketingAccessDomainService.class);

    /**
     * Validates that the event itself is currently open for sale.
     * Must be called before {@link #validateAccess} so that user-level checks
     * are never reached for unavailable events.
     *
     * @param event the event being purchased
     * @throws PermissionDeniedException if the event is not published or has already taken place
     */
    public void validateEventIsOpenForSale(Event event) {
        if (!event.isPublished()) {
            logger.warn("validateEventIsOpenForSale: event '{}' is not published — sale blocked.", event.getId());
            throw new PermissionDeniedException("purchase tickets — event is not published");
        }
        if (!LocalDateTime.now().isBefore(event.getEventDate())) {
            logger.warn("validateEventIsOpenForSale: event '{}' has already taken place — sale blocked.", event.getId());
            throw new PermissionDeniedException("purchase tickets — event has already taken place");
        }
        logger.debug("validateEventIsOpenForSale: event '{}' is open for sale.", event.getId());
    }

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
                logger.debug("validateAccess: user '{}' granted REGULAR access for event '{}'.", userId, event.getId());
            }
            case QUEUE -> {
                if (queue == null) {
                    logger.warn("validateAccess: user '{}' denied — QUEUE event '{}' has no queue configured.", userId, event.getId());
                    throw new PermissionDeniedException("purchase tickets for a queue-based event with no queue configured");
                }
                if (!queue.isUserActive(userId)) {
                    logger.warn("validateAccess: user '{}' denied — not in the active queue window for event '{}'.", userId, event.getId());
                    throw new PermissionDeniedException(userId, "purchase tickets — not in the active queue window");
                }
                logger.debug("validateAccess: user '{}' granted QUEUE access for event '{}'.", userId, event.getId());
            }
            case RAFFLE -> {
                if (authCode == null) {
                    logger.warn("validateAccess: user '{}' denied — no raffle authorization code for event '{}'.", userId, event.getId());
                    throw new PermissionDeniedException(userId, "purchase tickets — a raffle authorization code is required");
                }
                if (!authCode.isValidFor(userId, event.getId())) {
                    logger.warn("validateAccess: user '{}' denied — authorization code invalid or expired for event '{}'.", userId, event.getId());
                    throw new PermissionDeniedException(userId, "purchase tickets — authorization code is invalid or has expired");
                }
                logger.debug("validateAccess: user '{}' granted RAFFLE access for event '{}'.", userId, event.getId());
            }
        }
    }
}
