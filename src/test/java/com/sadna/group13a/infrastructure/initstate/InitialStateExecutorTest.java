package com.sadna.group13a.infrastructure.initstate;

import com.sadna.group13a.application.DTO.CompanyDTO;
import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.application.Services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialStateExecutorTest {

    @Mock private UserService userService;
    @Mock private CompanyService companyService;
    @Mock private EventService eventService;
    @InjectMocks private InitialStateExecutor executor;

    private static InitOperation op(String action, Map<String, Object> args, String bindTo) {
        return new InitOperation(action, args, bindTo);
    }

    @Test
    void executesSequenceAndResolvesBoundTokensAndIds() {
        when(userService.register("alice", "pw")).thenReturn(Result.<UserDTO>success(null));
        when(userService.login("alice", "pw")).thenReturn(Result.success("token-123"));
        when(companyService.createCompany("token-123", "MyCo", "desc")).thenReturn(Result.success(true));
        when(companyService.getMyCompanies("token-123"))
                .thenReturn(Result.success(List.of(new CompanyDTO("c1", "MyCo", "desc", null, "alice", List.of()))));
        when(eventService.createEvent(eq("token-123"), eq("c1"), eq("Jazz"), eq(""),
                any(LocalDateTime.class), eq(""), eq(""), eq(""))).thenReturn(Result.success("e1"));
        when(eventService.publishEvent("token-123", "e1")).thenReturn(Result.success());

        List<InitOperation> ops = List.of(
                op("register", Map.of("username", "alice", "password", "pw"), null),
                op("login", Map.of("username", "alice", "password", "pw"), "alice_token"),
                op("create-company", Map.of("token", "alice_token", "name", "MyCo", "description", "desc"), "my_co"),
                op("create-event", Map.of("token", "alice_token", "companyId", "my_co",
                        "title", "Jazz", "date", "2026-08-01T20:00"), "jazz"),
                op("publish-event", Map.of("token", "alice_token", "eventId", "jazz"), null)
        );

        executor.execute(ops);

        // Confirms bindings resolved: alice_token -> token-123, my_co -> c1, jazz -> e1
        verify(eventService).publishEvent("token-123", "e1");
    }

    @Test
    void abortsOnFirstFailedOperation() {
        when(userService.register("alice", "pw")).thenReturn(Result.failure("username taken"));

        List<InitOperation> ops = List.of(
                op("register", Map.of("username", "alice", "password", "pw"), null),
                op("login", Map.of("username", "alice", "password", "pw"), "alice_token")
        );

        assertThrows(InitialStateException.class, () -> executor.execute(ops));
        verify(userService, never()).login(any(), any());
    }

    @Test
    void rejectsUnknownAction() {
        assertThrows(InitialStateException.class,
                () -> executor.execute(List.of(op("frobnicate", Map.of(), null))));
    }

    @Test
    void rejectsMissingRequiredArgument() {
        assertThrows(InitialStateException.class,
                () -> executor.execute(List.of(op("register", Map.of("username", "alice"), null))));
    }
}
