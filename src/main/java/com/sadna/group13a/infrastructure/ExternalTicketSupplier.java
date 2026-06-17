package com.sadna.group13a.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Interfaces.TicketIssueRequest;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.config.ExternalSystemTimeoutProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real adapter for the external ticket-issuance system (issue #226).
 * Active only in the {@code prod} profile; {@link StubTicketSupplier} is used in all other profiles.
 *
 * <p>Talks to the configured HTTP endpoint ({@code app.ticketing.url}) using two actions:
 * <ul>
 *   <li>{@code issue_ticket}  — returns a ticket code, or {@code -1} on failure.</li>
 *   <li>{@code cancel_ticket} — returns {@code 1} on success, or {@code -1} on failure.</li>
 * </ul>
 *
 * <p>Issuance is atomic: if any single ticket fails, all tickets already issued in the
 * same call are cancelled before a failure is returned, so the caller can refund/roll back.
 */
@Service
@Profile("prod")
public class ExternalTicketSupplier implements ITicketSupplier {

    private static final Logger logger = LoggerFactory.getLogger(ExternalTicketSupplier.class);
    private static final String FAILURE = "-1";

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration readTimeout;

    public ExternalTicketSupplier(@Value("${app.ticketing.url}") String baseUrl,
                                  ObjectMapper objectMapper,
                                  ExternalSystemTimeoutProperties timeouts) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.readTimeout = Duration.ofMillis(timeouts.getReadTimeoutMs());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeouts.getConnectTimeoutMs()))
                .build();
    }

    @Override
    public boolean isConnected() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(readTimeout)
                    .GET()
                    .build();
            // Any HTTP response (even 4xx/405) means the service is reachable.
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception e) {
            logger.error("[TICKET] External ticket service unreachable at {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    @Override
    public Result<List<String>> issueTickets(String customerId, List<TicketIssueRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Result.success(List.of());
        }

        List<String> issued = new ArrayList<>();
        for (TicketIssueRequest req : requests) {
            Result<String> single = issueSingle(customerId, req);
            if (!single.isSuccess()) {
                logger.warn("[TICKET] Issuance failed for customer {} (event {}, zone {}): {} — rolling back {} ticket(s).",
                        customerId, req.eventId(), req.zone(), single.getErrorMessage(), issued.size());
                if (!issued.isEmpty()) {
                    cancelTickets(issued);
                }
                return Result.failure(single.getErrorMessage());
            }
            issued.add(single.getOrThrow());
        }
        logger.info("[TICKET] Issued {} ticket(s) for customer {}.", issued.size(), customerId);
        return Result.success(issued);
    }

    @Override
    public Result<Void> cancelTickets(List<String> ticketCodes) {
        if (ticketCodes == null || ticketCodes.isEmpty()) {
            return Result.success();
        }
        boolean allCancelled = true;
        for (String code : ticketCodes) {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("action_type", "cancel_ticket");
            body.put("ticket_id", code);
            String response = post(body);
            if (response == null || !"1".equals(normalize(response))) {
                allCancelled = false;
                logger.error("[TICKET] Failed to cancel ticket {} (response: {}).", code, response);
            }
        }
        logger.info("[TICKET] Cancellation requested for {} ticket(s).", ticketCodes.size());
        return allCancelled ? Result.success() : Result.failure("One or more tickets could not be cancelled.");
    }

    // ── Internal ──────────────────────────────────────────────────

    private Result<String> issueSingle(String customerId, TicketIssueRequest req) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("action_type", "issue_ticket");
        body.put("customer_id", customerId);
        body.put("event_id", req.eventId());
        body.put("zone", req.zone());
        if (req.seating()) {
            body.put("is_seating", "true");
            body.put("seats", "[{\"row\":" + req.row() + ",\"seat\":" + req.seat() + "}]");
        } else {
            body.put("quantity", "1");
        }

        String response = post(body);
        if (response == null) {
            return Result.failure("External ticket service is unreachable.");
        }
        String code = normalize(response);
        if (code.isEmpty() || FAILURE.equals(code)) {
            return Result.failure("External ticket service rejected the issuance.");
        }
        return Result.success(code);
    }

    /** Sends a JSON POST and returns the response body, or {@code null} on any transport error. */
    private String post(Map<String, String> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                logger.error("[TICKET] External ticket service returned HTTP {} for action {}.",
                        response.statusCode(), body.get("action_type"));
                return null;
            }
            return response.body();
        } catch (Exception e) {
            logger.error("[TICKET] Error calling external ticket service ({}): {}",
                    body.get("action_type"), e.getMessage());
            return null;
        }
    }

    /** Trims whitespace and strips a single layer of surrounding JSON quotes. */
    private String normalize(String raw) {
        String s = raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }
}
