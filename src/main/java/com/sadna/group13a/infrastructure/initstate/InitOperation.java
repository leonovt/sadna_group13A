package com.sadna.group13a.infrastructure.initstate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * A single use-case invocation described in the initial-state file (V3 issue #224).
 *
 * <p>JSON shape:</p>
 * <pre>
 * { "action": "login", "args": { "username": "alice", "password": "pw" }, "bindTo": "alice_token" }
 * </pre>
 *
 * @param action the use-case name (see {@link InitialStateExecutor} for the supported set)
 * @param args   named arguments; values that match a previously bound name are resolved to that value
 * @param bindTo optional name to bind this operation's returned value (token / id) for later reuse
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InitOperation(String action, Map<String, Object> args, String bindTo) {

    public Map<String, Object> args() {
        return args == null ? Map.of() : args;
    }
}
