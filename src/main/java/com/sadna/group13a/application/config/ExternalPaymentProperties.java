package com.sadna.group13a.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External payment service configuration (V3 issue #225), bound from {@code app.external.payment.*}.
 *
 * <p>Gateway <b>selection is profile-driven</b>, not property-driven: the real
 * {@code WsepPaymentGateway} is {@code @Profile("prod")} and the {@code StubPaymentGateway}
 * is {@code @Profile("!prod")}. This class only carries the WSEP base {@link #getUrl() url}
 * used by the real gateway. The {@code mode} field is retained for backward compatibility
 * with existing config but is not consulted for bean selection.</p>
 */
@ConfigurationProperties(prefix = "app.external.payment")
public class ExternalPaymentProperties {

    /** Retained for backward compatibility; bean selection is profile-driven (see class javadoc). */
    private String mode = "stub";

    /** Base URL of the external WSEP payment service (used only by the prod-profile gateway). */
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
