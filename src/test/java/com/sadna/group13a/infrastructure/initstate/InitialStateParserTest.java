package com.sadna.group13a.infrastructure.initstate;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InitialStateParserTest {

    private final InitialStateParser parser = new InitialStateParser();

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesOperationsWithArgsAndBinding() {
        String json = """
            [
              { "action": "register", "args": { "username": "alice", "password": "pw" } },
              { "action": "login", "args": { "username": "alice", "password": "pw" }, "bindTo": "alice_token" }
            ]
            """;

        List<InitOperation> ops = parser.parse(stream(json));

        assertEquals(2, ops.size());
        assertEquals("register", ops.get(0).action());
        assertEquals("alice", ops.get(0).args().get("username"));
        assertEquals("login", ops.get(1).action());
        assertEquals("alice_token", ops.get(1).bindTo());
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(InitialStateException.class, () -> parser.parse(stream("this is not json")));
    }

    @Test
    void rejectsOperationWithoutAction() {
        assertThrows(InitialStateException.class,
                () -> parser.parse(stream("[ { \"args\": { \"x\": \"y\" } } ]")));
    }
}
