package com.sadna.group13a.infrastructure;

import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Production implementation of {@link ITicketSupplier} that calls the WSEP
 * external ticket issuance endpoint.
 *
 * <p>Tickets are issued one at a time — a single {@code issue_ticket} POST per
 * ticket — and the returned ticket code (result field) is collected into the
 * list returned to the caller.  Similarly, cancellation sends one POST per
 * ticket code.
 *
 * <p>A {@code result} of {@code "-1"} from the external system signals failure
 * and causes the entire operation to return {@link Result#failure}.
 *
 * <p>Only active under the {@code prod} Spring profile.
 */
@Service
@Profile("prod")
public class RealTicketSupplier implements ITicketSupplier {

    private static final Logger logger = LoggerFactory.getLogger(RealTicketSupplier.class);

    private final ExternalSystemClient client;

    public RealTicketSupplier(ExternalSystemClient client) {
        this.client = client;
    }

    // ── ITicketSupplier ───────────────────────────────────────────────────────

    @Override
    public boolean isConnected() {
        try {
            Map<String, String> response = client.post(Map.of("action_type", "handshake"));
            boolean ok = !ExternalSystemClient.FAILURE_CODE.equals(response.get("result"));
            logger.info("[TICKET] Handshake → {}", ok ? "OK" : "FAILED");
            return ok;
        } catch (Exception e) {
            logger.warn("[TICKET] Handshake failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Issues {@code quantity} tickets for the given order, one external call per
     * ticket.  If any individual issuance fails, the already-issued codes are
     * returned inside a {@link Result#failure} so callers can attempt
     * cancellation of the partial batch.
     *
     * @param orderId  internal receipt / order ID passed as the event identifier
     * @param quantity number of tickets to issue
     * @return {@link Result} carrying the list of issued ticket codes on full
     *         success, or failure on the first rejection
     */
    @Override
    public Result<List<String>> issueTickets(String orderId, int quantity) {
        List<String> codes = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            Map<String, String> params = Map.of(
                    "action_type", "issue_ticket",
                    "event_id",    orderId
            );
            try {
                Map<String, String> response = client.post(params);
                String code = response.get("result");
                if (ExternalSystemClient.FAILURE_CODE.equals(code)) {
                    logger.error("[TICKET] Issue ticket FAILED for orderId={} ticket#{}", orderId, i + 1);
                    return Result.failure("Ticket issuance rejected by ticket provider");
                }
                codes.add(code);
                logger.debug("[TICKET] Issued ticket {} for orderId={}", code, orderId);
            } catch (Exception e) {
                logger.error("[TICKET] HTTP error issuing ticket #{} for orderId={}: {}",
                        i + 1, orderId, e.getMessage());
                return Result.failure("Ticket provider unreachable: " + e.getMessage());
            }
        }
        logger.info("[TICKET] Issued {} ticket(s) for orderId={}", codes.size(), orderId);
        return Result.success(codes);
    }

    /**
     * Cancels previously issued tickets, one external call per ticket code.
     * Logs individual failures but continues cancelling the remaining codes so
     * that as many tickets as possible are invalidated.
     *
     * @param ticketCodes the ticket codes to cancel
     * @return {@link Result#success()} if all cancellations succeeded, or
     *         {@link Result#failure} if at least one was rejected
     */
    @Override
    public Result<Void> cancelTickets(List<String> ticketCodes) {
        boolean anyFailed = false;
        for (String code : ticketCodes) {
            Map<String, String> params = Map.of(
                    "action_type", "cancel_ticket",
                    "ticket_id",   code
            );
            try {
                Map<String, String> response = client.post(params);
                if (ExternalSystemClient.FAILURE_CODE.equals(response.get("result"))) {
                    logger.error("[TICKET] Cancel ticket FAILED for code={}", code);
                    anyFailed = true;
                } else {
                    logger.debug("[TICKET] Cancelled ticket {}", code);
                }
            } catch (Exception e) {
                logger.error("[TICKET] HTTP error cancelling ticket {}: {}", code, e.getMessage());
                anyFailed = true;
            }
        }
        if (anyFailed) {
            return Result.failure("One or more ticket cancellations failed");
        }
        logger.info("[TICKET] Cancelled {} ticket(s).", ticketCodes.size());
        return Result.success();
    }
}
