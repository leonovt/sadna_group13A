package com.sadna.group13a.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the dedicated test configuration (issue #231): proves a test profile exists and
 * can never point the suite at the real database or external endpoints. Runs fully offline
 * (just reads the classpath resource — no network, no Spring context).
 */
@DisplayName("Issue #231 — Dedicated test configuration is safe & offline")
class DedicatedTestConfigurationTest {

    private static final String REAL_EXTERNAL_HOST = "koyeb.app";

    private Properties loadTestProperties() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/application-test.properties")) {
            assertNotNull(in, "application-test.properties must exist under src/test/resources");
            Properties props = new Properties();
            props.load(in);
            return props;
        }
    }

    @Test
    @DisplayName("application-test.properties exists on the test classpath")
    void testConfigFileExists() throws Exception {
        assertFalse(loadTestProperties().isEmpty(), "test configuration must define properties");
    }

    @Test
    @DisplayName("No external URL points to the real endpoint; all are local stubs")
    void externalUrlsAreLocalStubs() throws Exception {
        Properties props = loadTestProperties();
        for (String key : new String[]{"app.ticketing.url", "external.payment.url", "external.tickets.url"}) {
            String url = props.getProperty(key);
            assertNotNull(url, "test config must override '" + key + "'");
            assertFalse(url.contains(REAL_EXTERNAL_HOST),
                    key + " must NOT point to the real external endpoint");
            assertTrue(url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1"),
                    key + " must point to a local stub, was: " + url);
        }
    }

    @Test
    @DisplayName("No real/production datasource is configured (in-memory only)")
    void noProductionDatasource() throws Exception {
        String datasourceUrl = loadTestProperties().getProperty("spring.datasource.url");
        if (datasourceUrl != null) {
            assertTrue(datasourceUrl.contains("jdbc:h2:mem"),
                    "any configured datasource must be an in-memory H2 database, was: " + datasourceUrl);
        }
    }

    @Test
    @DisplayName("Admin credentials are test-only, not production defaults")
    void adminCredentialsAreTestOnly() throws Exception {
        Properties props = loadTestProperties();
        assertEquals("test-admin", props.getProperty("app.admin.username"));
        assertNotEquals("admin123", props.getProperty("app.admin.password"),
                "test profile must not reuse the production admin password");
    }
}
