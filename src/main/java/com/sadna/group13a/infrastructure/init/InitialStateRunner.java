package com.sadna.group13a.infrastructure.init;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs the initial-state file after the platform admin has been bootstrapped (issue #230).
 * Ordered after {@code PlatformBootstrap} (@Order(1)); any failure propagates and aborts
 * start-up so the system never boots into a partially-initialized state.
 */
@Component
@Order(2)
public class InitialStateRunner implements ApplicationRunner {

    private final InitialStateLoader loader;
    private final String initStateFile;

    public InitialStateRunner(InitialStateLoader loader,
                              @Value("${app.init.state-file:}") String initStateFile) {
        this.loader = loader;
        this.initStateFile = initStateFile;
    }

    @Override
    public void run(ApplicationArguments args) {
        loader.runFromFile(initStateFile);
    }
}
