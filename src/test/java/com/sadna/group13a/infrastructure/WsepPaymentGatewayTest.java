package com.sadna.group13a.infrastructure;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.config.ExternalPaymentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.POST;

/**
 * Robustness tests for the external WSEP payment gateway (V3 issue #225, spec §5): timeouts,
 * server errors, malformed bodies and {@code -1} results must all degrade gracefully to a
 * {@link Result} failure (or {@code false}) rather than throwing.
 */
class WsepPaymentGatewayTest {

    private static final String URL = "https://wsep.test/";
    private static final String CARD_JSON = """
        {"cardNumber":"4111111111111111","month":"4","year":"2026",
         "holder":"Alice","cvv":"123","id":"20444444","currency":"USD"}
        """;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private WsepPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        ExternalPaymentProperties props = new ExternalPaymentProperties();
        props.setMode("wsep");
        props.setUrl(URL);
        gateway = new WsepPaymentGateway(restTemplate, props);
    }

    // ── handshake / isConnected ──────────────────────────────────────

    @Test
    @DisplayName("isConnected: true when handshake returns OK")
    void isConnectedTrueOnOk() {
        server.expect(requestTo(URL)).andExpect(method(POST))
              .andExpect(content().string(containsString("action_type=handshake")))
              .andRespond(withSuccess("OK", MediaType.TEXT_PLAIN));
        assertTrue(gateway.isConnected());
        server.verify();
    }

    @Test
    @DisplayName("isConnected: false on server error")
    void isConnectedFalseOnServerError() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertFalse(gateway.isConnected());
    }

    @Test
    @DisplayName("isConnected: false on unexpected body")
    void isConnectedFalseOnUnexpectedBody() {
        server.expect(requestTo(URL)).andRespond(withSuccess("NOPE", MediaType.TEXT_PLAIN));
        assertFalse(gateway.isConnected());
    }

    // ── pay ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("processPayment: success returns the transaction id")
    void processPaymentSuccess() {
        server.expect(requestTo(URL)).andExpect(method(POST))
              .andExpect(content().string(containsString("action_type=pay")))
              .andRespond(withSuccess("12345", MediaType.TEXT_PLAIN));

        Result<String> result = gateway.processPayment(100.0, CARD_JSON);

        assertTrue(result.isSuccess());
        assertEquals("12345", result.getOrThrow());
        server.verify();
    }

    @Test
    @DisplayName("processPayment: -1 response is a declined payment")
    void processPaymentDeclined() {
        server.expect(requestTo(URL)).andRespond(withSuccess("-1", MediaType.TEXT_PLAIN));
        assertFalse(gateway.processPayment(100.0, CARD_JSON).isSuccess());
    }

    @Test
    @DisplayName("processPayment: malformed body fails gracefully")
    void processPaymentMalformed() {
        server.expect(requestTo(URL)).andRespond(withSuccess("not-a-number", MediaType.TEXT_PLAIN));
        assertFalse(gateway.processPayment(100.0, CARD_JSON).isSuccess());
    }

    @Test
    @DisplayName("processPayment: out-of-range transaction id fails")
    void processPaymentOutOfRange() {
        server.expect(requestTo(URL)).andRespond(withSuccess("42", MediaType.TEXT_PLAIN));
        assertFalse(gateway.processPayment(100.0, CARD_JSON).isSuccess());
    }

    @Test
    @DisplayName("processPayment: server error fails without throwing")
    void processPaymentServerError() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertFalse(gateway.processPayment(100.0, CARD_JSON).isSuccess());
    }

    @Test
    @DisplayName("processPayment: connection failure (timeout) fails without throwing")
    void processPaymentConnectionFailure() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("connection reset")));
        assertFalse(gateway.processPayment(100.0, CARD_JSON).isSuccess());
    }

    @Test
    @DisplayName("processPayment: invalid card details fail before any HTTP call")
    void processPaymentInvalidCard() {
        // No server expectation: parsing must fail before a request is sent.
        assertFalse(gateway.processPayment(100.0, "this is not json").isSuccess());
    }

    // ── refund ───────────────────────────────────────────────────────

    @Test
    @DisplayName("refundPayment: 1 response is success")
    void refundSuccess() {
        server.expect(requestTo(URL)).andExpect(method(POST))
              .andExpect(content().string(containsString("action_type=refund")))
              .andRespond(withSuccess("1", MediaType.TEXT_PLAIN));
        assertTrue(gateway.refundPayment("12345").isSuccess());
        server.verify();
    }

    @Test
    @DisplayName("refundPayment: -1 response is failure")
    void refundFailure() {
        server.expect(requestTo(URL)).andRespond(withSuccess("-1", MediaType.TEXT_PLAIN));
        assertFalse(gateway.refundPayment("12345").isSuccess());
    }

    @Test
    @DisplayName("refundPartial: issues a full WSEP refund")
    void refundPartialDelegatesToFullRefund() {
        server.expect(requestTo(URL)).andExpect(method(POST))
              .andExpect(content().string(containsString("action_type=refund")))
              .andRespond(withSuccess("1", MediaType.TEXT_PLAIN));
        assertTrue(gateway.refundPartial("12345", 30.0).isSuccess());
        server.verify();
    }
}
