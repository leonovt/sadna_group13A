package com.sadna.group13a.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External payment service configuration (V3 issue #225), bound from {@code app.external.payment.*}.
 *
 * <p>{@code mode} selects which {@code IPaymentGateway} bean is active:</p>
 * <ul>
 *   <li>{@code stub} (default) — the in-memory {@code StubPaymentGateway} (tests / local dev)</li>
 *   <li>{@code wsep} — the real {@code WsepPaymentGateway} calling {@link #getUrl()}</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.external.payment")
public class ExternalPaymentProperties {

    /** Active gateway: {@code stub} or {@code wsep}. */
    private String mode = "stub";

    /** Base URL of the external WSEP payment service (used only when {@code mode=wsep}). */
    private String url;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
