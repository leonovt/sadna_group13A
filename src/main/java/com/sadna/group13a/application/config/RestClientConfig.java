package com.sadna.group13a.application.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP client used to call external systems (V3 issue #225).
 *
 * <p>Bounded connect/read timeouts ensure the system stays responsive and fails fast when an
 * external service (e.g. the payment gateway) is slow or unreachable, rather than hanging.
 * Timeout values come from {@link ExternalSystemTimeoutProperties} (issue #241) — not hardcoded —
 * so they can be tuned per environment via {@code app.external.connect-timeout-ms} /
 * {@code app.external.read-timeout-ms}.</p>
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate paymentRestTemplate(RestTemplateBuilder builder, ExternalSystemTimeoutProperties timeouts) {
        return builder
                .setConnectTimeout(Duration.ofMillis(timeouts.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(timeouts.getReadTimeoutMs()))
                .build();
    }
}
