package com.sadna.group13a.infrastructure.initstate;

import com.sadna.group13a.application.DTO.CompanyDTO;
import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.application.Services.OrderService;
import com.sadna.group13a.application.Services.UserService;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
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
    @Mock private OrderService orderService;
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
    void runsBroadenedStaffStory_appointOwnerAndAcceptNomination() {
        when(userService.login("alice", "pw")).thenReturn(Result.success("alice-tok"));
        when(userService.login("bob", "pw")).thenReturn(Result.success("bob-tok"));
        when(companyService.appointOwner("alice-tok", "c1", "bob")).thenReturn(Result.success());
        when(companyService.acceptNomination("bob-tok", "c1")).thenReturn(Result.success());

        List<InitOperation> ops = List.of(
                op("login", Map.of("username", "alice", "password", "pw"), "alice_tok"),
                op("login", Map.of("username", "bob", "password", "pw"), "bob_tok"),
                op("appoint-owner", Map.of("token", "alice_tok", "companyId", "c1", "targetUsername", "bob"), null),
                op("accept-nomination", Map.of("token", "bob_tok", "companyId", "c1"), null)
        );

        executor.execute(ops);

        verify(companyService).appointOwner("alice-tok", "c1", "bob");
        verify(companyService).acceptNomination("bob-tok", "c1");
    }

    @Test
    void appointManagerParsesPermissions() {
        when(companyService.appointManager(eq("tok"), eq("c1"), eq("bob"),
                eq(java.util.Set.of(CompanyPermission.MANAGE_EVENTS, CompanyPermission.VIEW_REPORTS))))
                .thenReturn(Result.success());

        executor.execute(List.of(op("appoint-manager",
                Map.of("token", "tok", "companyId", "c1", "targetUsername", "bob",
                        "permissions", "MANAGE_EVENTS, VIEW_REPORTS"), null)));

        verify(companyService).appointManager(eq("tok"), eq("c1"), eq("bob"),
                eq(java.util.Set.of(CompanyPermission.MANAGE_EVENTS, CompanyPermission.VIEW_REPORTS)));
    }

    @Test
    void addToCartThenCheckoutResolvesBoundOrderId() {
        when(orderService.addBatchItemsToCart("tok", "e1", "z1", null, 2))
                .thenReturn(Result.success("order-9"));
        when(orderService.executeCheckout(eq("tok"), eq("order-9"), any(), eq("{\"card\":\"x\"}")))
                .thenReturn(Result.success(null));

        executor.execute(List.of(
                op("add-to-cart", Map.of("token", "tok", "eventId", "e1", "zoneId", "z1", "quantity", "2"), "cart"),
                op("checkout", Map.of("token", "tok", "orderId", "cart", "paymentDetails", "{\"card\":\"x\"}"), null)
        ));

        verify(orderService).executeCheckout(eq("tok"), eq("order-9"), any(), eq("{\"card\":\"x\"}"));
    }

    @Test
    void rejectsInvalidPermission() {
        assertThrows(InitialStateException.class, () -> executor.execute(List.of(
                op("appoint-manager", Map.of("token", "t", "companyId", "c", "targetUsername", "b",
                        "permissions", "NOT_A_PERMISSION"), null))));
    }

    @Test
    void rejectsInvalidSaleMode() {
        assertThrows(InitialStateException.class, () -> executor.execute(List.of(
                op("set-sale-mode", Map.of("token", "t", "eventId", "e", "mode", "BOGUS"), null))));
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
