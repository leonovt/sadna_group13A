package com.sadna.group13a.infrastructure;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.SystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class PlatformBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(PlatformBootstrap.class);

    private final SystemService systemService;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    public PlatformBootstrap(SystemService systemService) {
        this.systemService = systemService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Result<Void> result = systemService.initializePlatform(adminUsername, adminPassword);
        if (result.isSuccess()) {
            logger.info("Platform initialized. Root admin account: '{}'.", adminUsername);
        } else {
            logger.warn("Platform initialization skipped: {}", result.getErrorMessage());
        }
    }
}
