package com.sadna.group13a.application.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies issue #241: the payment RestTemplate's connect/read timeouts come from
 * {@link ExternalSystemTimeoutProperties} — not hardcoded — by building it with custom
 * (non-default) values and inspecting the resulting request factory.
 */
class RestClientConfigTest {

    private final RestClientConfig config = new RestClientConfig();

    @Test
    @DisplayName("paymentRestTemplate applies the configured (non-default) connect/read timeouts")
    void appliesConfiguredTimeouts() throws Exception {
        ExternalSystemTimeoutProperties timeouts = new ExternalSystemTimeoutProperties();
        timeouts.setConnectTimeoutMs(1234);
        timeouts.setReadTimeoutMs(5678);

        RestTemplate restTemplate = config.paymentRestTemplate(new RestTemplateBuilder(), timeouts);

        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        assertEquals(SimpleClientHttpRequestFactory.class, factory.getClass());

        assertEquals(1234, readPrivateInt(factory, "connectTimeout"));
        assertEquals(5678, readPrivateInt(factory, "readTimeout"));
    }

    private static int readPrivateInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
