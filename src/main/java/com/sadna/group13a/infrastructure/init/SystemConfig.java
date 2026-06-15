package com.sadna.group13a.infrastructure.init;

/**
 * Snapshot of the start-up configuration that must be valid for the system to boot
 * (issue #230). {@code initStateFile} is optional (blank = start with a clean state).
 */
public record SystemConfig(
        String adminUsername,
        String adminPassword,
        String initStateFile
) {}
