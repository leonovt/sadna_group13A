package com.sadna.group13a.infrastructure.init;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Validates the start-up configuration as the context is built (issue #230).
 * Validation runs in the constructor, so an invalid configuration fails bean creation
 * and the application refuses to start.
 */
@Component
public class SystemStartupConfigValidator {

    public SystemStartupConfigValidator(
            @Value("${app.admin.username:}") String adminUsername,
            @Value("${app.admin.password:}") String adminPassword,
            @Value("${app.init.state-file:}") String initStateFile) {
        SystemConfigValidator.validate(new SystemConfig(adminUsername, adminPassword, initStateFile));
    }
}
