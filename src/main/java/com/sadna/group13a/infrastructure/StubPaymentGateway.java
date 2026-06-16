package com.sadna.group13a.infrastructure;

import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * In-memory payment gateway used by default and in tests. Active unless
 * {@code app.external.payment.mode=wsep}, in which case {@link WsepPaymentGateway} replaces it.
 */
@Service
@ConditionalOnProperty(name = "app.external.payment.mode", havingValue = "stub", matchIfMissing = true)
public class StubPaymentGateway implements IPaymentGateway {

    private static final Logger logger = LoggerFactory.getLogger(StubPaymentGateway.class);

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public Result<String> processPayment(double amount, String paymentDetails) {
        String transactionId = "TXN-" + UUID.randomUUID();
        logger.info("[PAYMENT] Stub processed payment of {}. Transaction: {}.", amount, transactionId);
        return Result.success(transactionId);
    }

    @Override
    public Result<Void> refundPayment(String transactionId) {
        logger.info("[PAYMENT] Stub refunded transaction {}.", transactionId);
        return Result.success();
    }

    @Override
    public Result<Void> refundPartial(String transactionId, double amount) {
        logger.info("[PAYMENT] Stub partially refunded {} of transaction {}.", amount, transactionId);
        return Result.success();
    }
}
