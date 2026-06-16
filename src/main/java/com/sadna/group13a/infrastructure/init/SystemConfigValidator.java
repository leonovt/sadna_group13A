package com.sadna.group13a.infrastructure.init;

/**
 * Validates the start-up configuration (issue #230). A missing required field or an
 * invalid value causes a {@link SystemInitializationException}, so the system refuses
 * to start rather than booting in a broken state.
 */
public final class SystemConfigValidator {

    static final int MIN_PASSWORD_LENGTH = 4;

    private SystemConfigValidator() {}

    public static void validate(SystemConfig config) {
        if (config == null) {
            throw new SystemInitializationException("Invalid configuration: configuration is missing.");
        }
        if (isBlank(config.adminUsername())) {
            throw new SystemInitializationException("Invalid configuration: 'app.admin.username' is required.");
        }
        if (isBlank(config.adminPassword())) {
            throw new SystemInitializationException("Invalid configuration: 'app.admin.password' is required.");
        }
        if (config.adminPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new SystemInitializationException(
                    "Invalid configuration: 'app.admin.password' must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
