package com.sadna.group13a.infrastructure.initstate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Parses an initial-state JSON document (a list of {@link InitOperation}s) (V3 issue #224).
 */
@Component
public class InitialStateParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Reads and parses the operations from the given stream.
     *
     * @throws InitialStateException if the content is not valid JSON or not a list of operations
     */
    public List<InitOperation> parse(InputStream in) {
        try {
            List<InitOperation> operations = objectMapper.readValue(in, new TypeReference<>() {});
            if (operations == null) {
                throw new InitialStateException("Initial-state file is empty.");
            }
            for (int i = 0; i < operations.size(); i++) {
                InitOperation op = operations.get(i);
                if (op == null || op.action() == null || op.action().isBlank()) {
                    throw new InitialStateException("Operation #" + i + " is missing an 'action'.");
                }
            }
            return operations;
        } catch (InitialStateException e) {
            throw e;
        } catch (Exception e) {
            throw new InitialStateException("Failed to parse initial-state file: " + e.getMessage(), e);
        }
    }
}
