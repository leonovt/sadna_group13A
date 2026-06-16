package com.sadna.group13a.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Low-level HTTP adapter for the WSEP external payment and ticket system.
 *
 * <p>All actions (pay, refund, issue_ticket, cancel_ticket, handshake) are sent
 * as {@code application/x-www-form-urlencoded} POST bodies to the single
 * configured endpoint.  The response is parsed as a flat {@code Map<String,String>}.
 *
 * <p>A result value of {@code "-1"} in the response map always signals failure;
 * any other value is considered success.
 *
 * <p>This bean is only instantiated in the {@code prod} Spring profile.
 * Under every other profile the stubs ({@link StubPaymentGateway},
 * {@link StubTicketSupplier}) are used instead.
 */
@Component
@Profile("prod")
public class ExternalSystemClient {

    private static final Logger logger = LoggerFactory.getLogger(ExternalSystemClient.class);

    static final String FAILURE_CODE = "-1";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ExternalSystemClient(
            @Value("${external.system.url}") String baseUrl,
            @Value("${external.system.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${external.system.read-timeout-ms:10000}") int readTimeoutMs) {

        this.baseUrl = baseUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);

        logger.info("[ExternalSystem] client configured → {} (connect={}ms read={}ms)",
                baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * Sends a form-encoded POST with the given parameters and returns the
     * parsed response as a flat string map.
     *
     * @param params key-value pairs to encode in the request body
     * @return server response as a map; never null
     * @throws org.springframework.web.client.RestClientException on HTTP or I/O error
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> post(Map<String, String> params) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        params.forEach(body::add);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        logger.debug("[ExternalSystem] POST {} action={}", baseUrl, params.get("action_type"));

        Map<?, ?> raw = restTemplate.postForObject(baseUrl, request, Map.class);

        Map<String, String> result = new LinkedHashMap<>();
        if (raw != null) {
            raw.forEach((k, v) -> result.put(String.valueOf(k), v != null ? String.valueOf(v) : ""));
        }
        return result;
    }
}
