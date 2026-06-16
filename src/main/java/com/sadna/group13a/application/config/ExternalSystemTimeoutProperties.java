package com.sadna.group13a.application.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Shared HTTP connect/read timeouts for calls to the external payment and ticket-issuance
 * system (issue #241). Bound from {@code app.external.*}; both {@link RestClientConfig}
 * (used by {@code WsepPaymentGateway}) and {@code ExternalTicketSupplier} read these same
 * values rather than hardcoding their own, so the two external-call sites stay consistent
 * and configurable without code changes.
 */
@ConfigurationProperties(prefix = "app.external")
@Validated
public class ExternalSystemTimeoutProperties {

    /** TCP connection timeout in milliseconds when reaching the external system. */
    @Min(value = 1, message = "app.external.connect-timeout-ms must be at least 1")
    private int connectTimeoutMs = 5000;

    /** Socket read timeout in milliseconds while waiting for the external system's response. */
    @Min(value = 1, message = "app.external.read-timeout-ms must be at least 1")
    private int readTimeoutMs = 10000;

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
