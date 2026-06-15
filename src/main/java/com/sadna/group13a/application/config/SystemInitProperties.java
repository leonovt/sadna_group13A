package com.sadna.group13a.application.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * System startup parameters loaded from external configuration (V3 issue #223).
 *
 * <p>Bound from the {@code app.init.*} section of {@code application.yml} (or any
 * active profile / external config file). The class is {@link Validated}, so an
 * invalid or missing value makes the Spring context fail to start — the system
 * refuses to come up in a misconfigured state.</p>
 */
@ConfigurationProperties(prefix = "app.init")
@Validated
public class SystemInitProperties {

    /**
     * Maximum number of users who may hold tickets for a single event simultaneously
     * before additional visitors are routed to the virtual queue. Must be at least 1.
     * A missing key binds to {@code 0} and therefore fails validation (fail-fast).
     */
    @Min(value = 1, message = "app.init.max-concurrent-users-per-event must be at least 1")
    private int maxConcurrentUsersPerEvent;

    /**
     * Optional path to an initial-state JSON file executed at startup (V3 issue #224).
     * Blank/absent means no initial-state loading is performed.
     */
    private String initialStateFile;

    public int getMaxConcurrentUsersPerEvent() {
        return maxConcurrentUsersPerEvent;
    }

    public void setMaxConcurrentUsersPerEvent(int maxConcurrentUsersPerEvent) {
        this.maxConcurrentUsersPerEvent = maxConcurrentUsersPerEvent;
    }

    public String getInitialStateFile() {
        return initialStateFile;
    }

    public void setInitialStateFile(String initialStateFile) {
        this.initialStateFile = initialStateFile;
    }
}
