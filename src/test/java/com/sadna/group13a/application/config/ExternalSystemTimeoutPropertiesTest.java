package com.sadna.group13a.application.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the issue #241 requirement: external-call timeouts are configurable (not
 * hardcoded), have sane defaults when unset, and fail fast on a nonsensical value.
 */
class ExternalSystemTimeoutPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(ExternalSystemTimeoutProperties.class)
    static class TestConfig {
    }

    @Test
    @DisplayName("Defaults apply when no timeout config is given (5s connect / 10s read)")
    void defaultsApplyWhenUnset() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            ExternalSystemTimeoutProperties props = ctx.getBean(ExternalSystemTimeoutProperties.class);
            assertThat(props.getConnectTimeoutMs()).isEqualTo(5000);
            assertThat(props.getReadTimeoutMs()).isEqualTo(10000);
        });
    }

    @Test
    @DisplayName("Custom values from config override the defaults")
    void customValuesOverrideDefaults() {
        runner.withPropertyValues("app.external.connect-timeout-ms=1234", "app.external.read-timeout-ms=5678")
              .run(ctx -> {
                  assertThat(ctx).hasNotFailed();
                  ExternalSystemTimeoutProperties props = ctx.getBean(ExternalSystemTimeoutProperties.class);
                  assertThat(props.getConnectTimeoutMs()).isEqualTo(1234);
                  assertThat(props.getReadTimeoutMs()).isEqualTo(5678);
              });
    }

    @Test
    @DisplayName("Context fails to start when connect-timeout-ms is not positive")
    void failsWhenConnectTimeoutNotPositive() {
        runner.withPropertyValues("app.external.connect-timeout-ms=0")
              .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("Context fails to start when read-timeout-ms is not positive")
    void failsWhenReadTimeoutNotPositive() {
        runner.withPropertyValues("app.external.read-timeout-ms=-1")
              .run(ctx -> assertThat(ctx).hasFailed());
    }
}
