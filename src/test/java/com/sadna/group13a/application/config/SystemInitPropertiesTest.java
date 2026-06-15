package com.sadna.group13a.application.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the V3 #223 fail-fast requirement: the system must not start with an
 * invalid or missing startup configuration.
 */
class SystemInitPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(SystemInitProperties.class)
    static class TestConfig {
    }

    @Test
    @DisplayName("Context starts and binds values when config is valid")
    void startsWithValidConfig() {
        runner.withPropertyValues("app.init.max-concurrent-users-per-event=100",
                                  "app.init.initial-state-file=")
              .run(ctx -> {
                  assertThat(ctx).hasNotFailed();
                  SystemInitProperties props = ctx.getBean(SystemInitProperties.class);
                  assertThat(props.getMaxConcurrentUsersPerEvent()).isEqualTo(100);
              });
    }

    @Test
    @DisplayName("Context fails to start when the queue threshold is below 1")
    void failsWhenThresholdBelowOne() {
        runner.withPropertyValues("app.init.max-concurrent-users-per-event=0")
              .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("Context fails to start when the queue threshold is missing")
    void failsWhenThresholdMissing() {
        runner.run(ctx -> assertThat(ctx).hasFailed());
    }
}
