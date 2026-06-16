package com.sadna.group13a.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Interfaces.TicketIssueRequest;
import com.sadna.group13a.application.Result;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link ExternalTicketSupplier} against a real (embedded) HTTP server,
 * so the adapter's request shaping, response parsing, and rollback behaviour are
 * exercised without depending on the live external service (issue #226).
 */
@DisplayName("ExternalTicketSupplier — external ticket issuance integration")
class ExternalTicketSupplierTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger issueCounter = new AtomicInteger(0);
    private final List<String> cancelledTickets = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response;
            if (body.contains("\"action_type\":\"cancel_ticket\"")) {
                cancelledTickets.add(extract(body, "ticket_id"));
                response = "1";
            } else if (body.contains("\"event_id\":\"FAIL\"")) {
                response = "-1";                       // simulate issuance rejection
            } else {
                response = "CODE-" + issueCounter.incrementAndGet();
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private ExternalTicketSupplier supplier() {
        return new ExternalTicketSupplier(baseUrl, new ObjectMapper());
    }

    @Test
    @DisplayName("isConnected returns true when the service is reachable")
    void isConnected_whenReachable_returnsTrue() {
        assertTrue(supplier().isConnected());
    }

    @Test
    @DisplayName("isConnected returns false when the service is unreachable")
    void isConnected_whenUnreachable_returnsFalse() {
        int deadPort = server.getAddress().getPort();
        server.stop(0);
        ExternalTicketSupplier deadSupplier = new ExternalTicketSupplier("http://localhost:" + deadPort + "/", new ObjectMapper());
        assertFalse(deadSupplier.isConnected());
    }

    @Test
    @DisplayName("Issues one code per request for standing and seated zones")
    void issueTickets_success_returnsCodePerRequest() {
        List<TicketIssueRequest> requests = List.of(
                new TicketIssueRequest("EVT-1", "General Standing", false, 0, 0),
                new TicketIssueRequest("EVT-1", "VIP Balcony", true, 4, 12));

        Result<List<String>> result = supplier().issueTickets("cust-1", requests);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getOrThrow().size());
        assertEquals(List.of("CODE-1", "CODE-2"), result.getOrThrow());
        assertTrue(cancelledTickets.isEmpty(), "no cancellation on full success");
    }

    @Test
    @DisplayName("Empty request list issues nothing and succeeds")
    void issueTickets_emptyRequests_succeedsWithNoCodes() {
        Result<List<String>> result = supplier().issueTickets("cust-1", List.of());
        assertTrue(result.isSuccess());
        assertTrue(result.getOrThrow().isEmpty());
    }

    @Test
    @DisplayName("A -1 response rolls back: already-issued tickets are cancelled and failure returned")
    void issueTickets_partialFailure_cancelsAlreadyIssued() {
        List<TicketIssueRequest> requests = List.of(
                new TicketIssueRequest("EVT-1", "Zone A", false, 0, 0),   // -> CODE-1
                new TicketIssueRequest("FAIL", "Zone B", false, 0, 0));   // -> -1

        Result<List<String>> result = supplier().issueTickets("cust-1", requests);

        assertFalse(result.isSuccess());
        assertEquals(List.of("CODE-1"), cancelledTickets, "the first issued ticket must be cancelled on rollback");
    }

    @Test
    @DisplayName("cancelTickets posts one cancel per code and reports success")
    void cancelTickets_success() {
        Result<Void> result = supplier().cancelTickets(List.of("T-1", "T-2"));
        assertTrue(result.isSuccess());
        assertEquals(List.of("T-1", "T-2"), cancelledTickets);
    }

    @Test
    @DisplayName("cancelTickets with empty list is a no-op success")
    void cancelTickets_empty_isNoOp() {
        Result<Void> result = supplier().cancelTickets(List.of());
        assertTrue(result.isSuccess());
        assertTrue(cancelledTickets.isEmpty());
    }

    private static String extract(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
