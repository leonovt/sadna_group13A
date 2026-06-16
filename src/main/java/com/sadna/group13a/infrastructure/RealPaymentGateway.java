package com.sadna.group13a.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.PaymentDetailsDTO;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Production implementation of {@link IPaymentGateway} that calls the WSEP
 * external payment endpoint.
 *
 * <p>The {@code paymentDetails} parameter received from the service layer is
 * expected to be a JSON-serialised {@link PaymentDetailsDTO}.  The real UI
 * checkout form serialises card fields to this format before invoking the
 * service.
 *
 * <p>Response convention from the external system:
 * <ul>
 *   <li>A {@code result} of {@code "-1"} → operation failed.</li>
 *   <li>Any other {@code result} value → success; for pay/refund it is the
 *       transaction ID.</li>
 * </ul>
 *
 * <p>Only active under the {@code prod} Spring profile.
 */
@Service
@Profile("prod")
public class RealPaymentGateway implements IPaymentGateway {

    private static final Logger logger = LoggerFactory.getLogger(RealPaymentGateway.class);

    private final ExternalSystemClient client;
    private final ObjectMapper objectMapper;

    public RealPaymentGateway(ExternalSystemClient client, ObjectMapper objectMapper) {
        this.client       = client;
        this.objectMapper = objectMapper;
    }

    // ── IPaymentGateway ───────────────────────────────────────────────────────

    @Override
    public boolean isConnected() {
        try {
            Map<String, String> response = client.post(Map.of("action_type", "handshake"));
            boolean ok = !ExternalSystemClient.FAILURE_CODE.equals(response.get("result"));
            logger.info("[PAYMENT] Handshake → {}", ok ? "OK" : "FAILED");
            return ok;
        } catch (Exception e) {
            logger.warn("[PAYMENT] Handshake failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Charges {@code amount} to the card described in {@code paymentDetailsJson}.
     *
     * @param amount             total amount to charge
     * @param paymentDetailsJson JSON-serialised {@link PaymentDetailsDTO}
     * @return {@link Result} carrying the transaction ID on success, or a
     *         failure message if the gateway declines
     */
    @Override
    public Result<String> processPayment(double amount, String paymentDetailsJson) {
        PaymentDetailsDTO details;
        try {
            details = objectMapper.readValue(paymentDetailsJson, PaymentDetailsDTO.class);
        } catch (Exception e) {
            logger.error("[PAYMENT] Cannot parse payment details JSON: {}", e.getMessage());
            return Result.failure("Invalid payment details format");
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type",  "pay");
        params.put("amount",       String.valueOf(amount));
        params.put("card_number",  details.cardNumber());
        params.put("month",        details.month());
        params.put("year",         details.year());
        params.put("holder",       details.holder());
        params.put("cvv",          details.cvv());
        params.put("id",           details.holderId());
        params.put("currency",     details.currency() != null ? details.currency() : "ILS");

        try {
            Map<String, String> response = client.post(params);
            String result = response.get("result");
            if (ExternalSystemClient.FAILURE_CODE.equals(result)) {
                logger.warn("[PAYMENT] Payment declined by gateway (amount={})", amount);
                return Result.failure("Payment declined by payment provider");
            }
            logger.info("[PAYMENT] Payment accepted. transactionId={}", result);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("[PAYMENT] HTTP error during pay: {}", e.getMessage());
            return Result.failure("Payment gateway unreachable: " + e.getMessage());
        }
    }

    @Override
    public Result<Void> refundPayment(String transactionId) {
        Map<String, String> params = Map.of(
                "action_type",     "refund",
                "transaction_id",  transactionId
        );
        try {
            Map<String, String> response = client.post(params);
            if (ExternalSystemClient.FAILURE_CODE.equals(response.get("result"))) {
                logger.error("[PAYMENT] Refund FAILED for transactionId={}", transactionId);
                return Result.failure("Refund rejected by payment provider");
            }
            logger.info("[PAYMENT] Refund OK. transactionId={}", transactionId);
            return Result.success();
        } catch (Exception e) {
            logger.error("[PAYMENT] HTTP error during refund (txn={}): {}", transactionId, e.getMessage());
            return Result.failure("Refund gateway unreachable: " + e.getMessage());
        }
    }

    @Override
    public Result<Void> refundPartial(String transactionId, double amount) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type",    "refund");
        params.put("transaction_id", transactionId);
        params.put("amount",         String.valueOf(amount));

        try {
            Map<String, String> response = client.post(params);
            if (ExternalSystemClient.FAILURE_CODE.equals(response.get("result"))) {
                logger.error("[PAYMENT] Partial refund FAILED. txn={} amount={}", transactionId, amount);
                return Result.failure("Partial refund rejected by payment provider");
            }
            logger.info("[PAYMENT] Partial refund OK. txn={} amount={}", transactionId, amount);
            return Result.success();
        } catch (Exception e) {
            logger.error("[PAYMENT] HTTP error during partial refund: {}", e.getMessage());
            return Result.failure("Refund gateway unreachable: " + e.getMessage());
        }
    }
}
