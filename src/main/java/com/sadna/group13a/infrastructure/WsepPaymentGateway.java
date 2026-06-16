package com.sadna.group13a.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.PaymentDetails;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.config.ExternalPaymentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * Real payment gateway backed by the external WSEP service (V3 issue #225).
 *
 * <p>Active only when {@code app.external.payment.mode=wsep}; otherwise {@link StubPaymentGateway}
 * is used. Every call is defensive: timeouts, non-2xx responses, malformed bodies and {@code -1}
 * results are turned into a {@link Result} failure (or {@code false} for {@link #isConnected()})
 * so that no external-system fault escapes as an exception (robustness, spec §5).</p>
 */
@Service
@ConditionalOnProperty(name = "app.external.payment.mode", havingValue = "wsep")
public class WsepPaymentGateway implements IPaymentGateway {

    private static final Logger logger = LoggerFactory.getLogger(WsepPaymentGateway.class);

    private final RestTemplate restTemplate;
    private final String url;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WsepPaymentGateway(RestTemplate paymentRestTemplate, ExternalPaymentProperties properties) {
        this.restTemplate = paymentRestTemplate;
        this.url = properties.getUrl();
    }

    @Override
    public boolean isConnected() {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("action_type", "handshake");
            String body = post(form);
            boolean ok = body != null && body.trim().replace("\"", "").equalsIgnoreCase("OK");
            if (!ok) {
                logger.warn("WSEP handshake returned an unexpected response: '{}'.", body);
            }
            return ok;
        } catch (Exception e) {
            logger.warn("WSEP handshake failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Result<String> processPayment(double amount, String paymentDetails) {
        PaymentDetails card;
        try {
            card = objectMapper.readValue(paymentDetails, PaymentDetails.class);
        } catch (Exception e) {
            logger.warn("Could not parse payment details for WSEP charge: {}", e.getMessage());
            return Result.failure("Invalid payment details.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("action_type", "pay");
        form.add("amount", BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString());
        form.add("currency", card.currency() == null || card.currency().isBlank() ? "USD" : card.currency());
        form.add("card_number", card.cardNumber());
        form.add("month", card.month());
        form.add("year", card.year());
        form.add("holder", card.holder());
        form.add("cvv", card.cvv());
        form.add("id", card.id());

        String body;
        try {
            body = post(form);
        } catch (Exception e) {
            logger.warn("WSEP payment request failed: {}", e.getMessage());
            return Result.failure("Payment service is unavailable. Please try again.");
        }

        Integer transactionId = parseInt(body);
        if (transactionId == null) {
            logger.error("WSEP payment returned a malformed response: '{}'.", body);
            return Result.failure("Payment service returned an invalid response.");
        }
        if (transactionId == -1) {
            return Result.failure("Payment was declined by the external service.");
        }
        if (transactionId < 10000 || transactionId > 100000) {
            logger.error("WSEP payment returned an out-of-range transaction id: {}.", transactionId);
            return Result.failure("Payment service returned an invalid transaction id.");
        }
        return Result.success(String.valueOf(transactionId));
    }

    @Override
    public Result<Void> refundPayment(String transactionId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("action_type", "refund");
        form.add("transaction_id", transactionId);

        String body;
        try {
            body = post(form);
        } catch (Exception e) {
            logger.warn("WSEP refund request failed for transaction {}: {}", transactionId, e.getMessage());
            return Result.failure("Refund service is unavailable.");
        }

        Integer result = parseInt(body);
        if (result != null && result == 1) {
            return Result.success();
        }
        logger.error("WSEP refund for transaction {} failed (response '{}').", transactionId, body);
        return Result.failure("Refund failed.");
    }

    /**
     * WSEP only supports refunding a whole transaction, and the system refunds the entire order on
     * cancellation/failure, so this issues a full refund and ignores {@code amount}.
     */
    @Override
    public Result<Void> refundPartial(String transactionId, double amount) {
        logger.info("WSEP does not support partial refunds; issuing a full refund for transaction {}.", transactionId);
        return refundPayment(transactionId);
    }

    private String post(MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> response =
                restTemplate.postForEntity(url, new HttpEntity<>(form, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("WSEP responded with HTTP " + response.getStatusCode());
        }
        return response.getBody();
    }

    private Integer parseInt(String body) {
        if (body == null) {
            return null;
        }
        try {
            return Integer.parseInt(body.trim().replace("\"", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
