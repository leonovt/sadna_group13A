package com.sadna.group13a.infrastructure.init;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs the initial-state file after the platform admin has been bootstrapped (issue #230).
 * Ordered after PlatformBootstrap (@1), DemoDataSeeder (@2), and InitialStateLoader (@3).
 * Any failure propagates and aborts start-up.
 */
@Component
@Order(4)
public class InitialStateRunner implements ApplicationRunner {

    private final InitialStateLoader loader;
    private final String initStateFile;

    public InitialStateRunner(@Qualifier("textInitialStateLoader") InitialStateLoader loader,
                              @Value("${app.init.state-file:}") String initStateFile) {
        this.loader = loader;
        this.initStateFile = initStateFile;
    }

    @Override
    public void run(ApplicationArguments args) {
        loader.runFromFile(initStateFile);
    }
}
