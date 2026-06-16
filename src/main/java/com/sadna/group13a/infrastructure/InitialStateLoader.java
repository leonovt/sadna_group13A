package com.sadna.group13a.infrastructure;

import com.sadna.group13a.application.config.SystemInitProperties;
import com.sadna.group13a.infrastructure.initstate.InitOperation;
import com.sadna.group13a.infrastructure.initstate.InitialStateException;
import com.sadna.group13a.infrastructure.initstate.InitialStateExecutor;
import com.sadna.group13a.infrastructure.initstate.InitialStateParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Loads an optional initial system state at startup (V3 issue #224).
 *
 * <p>Runs after {@link PlatformBootstrap} (which creates the root admin). When
 * {@code app.init.initial-state-file} is configured, the referenced JSON file is parsed and
 * its operations are replayed through the application layer. Initialization is all-or-nothing:
 * any failure propagates out of this runner, so the Spring context fails and the application
 * does not start.</p>
 *
 * <p>The configured path is resolved as a filesystem path (absolute, or relative to the working
 * directory); if not found there it is looked up on the classpath. A {@code classpath:} prefix
 * forces a classpath lookup.</p>
 */
@Component
@Order(3)
public class InitialStateLoader implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(InitialStateLoader.class);

    private final SystemInitProperties properties;
    private final InitialStateParser parser;
    private final InitialStateExecutor executor;

    public InitialStateLoader(SystemInitProperties properties,
                              InitialStateParser parser,
                              InitialStateExecutor executor) {
        this.properties = properties;
        this.parser = parser;
        this.executor = executor;
    }

    @Override
    public void run(ApplicationArguments args) {
        String path = properties.getInitialStateFile();
        if (path == null || path.isBlank()) {
            logger.info("No initial-state file configured; skipping initial-state loading.");
            return;
        }

        logger.info("Loading initial state from '{}'.", path);
        try (InputStream in = open(path.trim())) {
            List<InitOperation> operations = parser.parse(in);
            executor.execute(operations);
        } catch (IOException e) {
            throw new InitialStateException("Failed to read initial-state file '" + path + "': " + e.getMessage(), e);
        }
    }

    private InputStream open(String path) throws IOException {
        if (!path.startsWith("classpath:")) {
            File file = new File(path);
            if (file.isFile()) {
                return new FileInputStream(file);
            }
        }
        String classpathLocation = path.startsWith("classpath:") ? path.substring("classpath:".length()) : path;
        InputStream in = getClass().getClassLoader().getResourceAsStream(classpathLocation);
        if (in == null) {
            throw new InitialStateException("Initial-state file not found: '" + path + "'.");
        }
        return in;
    }
}
