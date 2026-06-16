package com.sadna.group13a.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("InMemoryNotificationService")
class InMemoryNotificationServiceTest {

    private InMemoryNotificationService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryNotificationService();
    }

    @Test
    @DisplayName("notifyQueueTurnArrived — logs without throwing")
    void notifyQueueTurnArrived_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyQueueTurnArrived("u1", "e1", LocalDateTime.now()));
    }

    @Test
    @DisplayName("notifyOrderCompleted — logs without throwing")
    void notifyOrderCompleted_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyOrderCompleted("u1", "r1", 99.0));
    }

    @Test
    @DisplayName("notifyUserBanned — logs without throwing")
    void notifyUserBanned_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyUserBanned("u1", "admin1"));
    }

    @Test
    @DisplayName("notifyUserSuspended with date — logs without throwing")
    void notifyUserSuspended_withDate_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyUserSuspended("u1", LocalDateTime.now().plusDays(7)));
    }

    @Test
    @DisplayName("notifyUserSuspended with null — logs indefinitely without throwing")
    void notifyUserSuspended_nullDate_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyUserSuspended("u1", null));
    }

    @Test
    @DisplayName("notifyCompanyClosed — logs for each staff without throwing")
    void notifyCompanyClosed_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyCompanyClosed(List.of("s1", "s2"), "c1", "admin1"));
    }

    @Test
    @DisplayName("notifyRaffleDrawn — logs without throwing")
    void notifyRaffleDrawn_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyRaffleDrawn(List.of("loser1", "loser2"), "e1", 3));
    }

    @Test
    @DisplayName("notifyActionFailed — logs without throwing")
    void notifyActionFailed_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyActionFailed("u1", "payment failed"));
    }

    @Test
    @DisplayName("notifyCompanySuspended — logs for each staff without throwing")
    void notifyCompanySuspended_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyCompanySuspended(List.of("s1"), "c1"));
    }

    @Test
    @DisplayName("notifyCompanyReopened — logs for each staff without throwing")
    void notifyCompanyReopened_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyCompanyReopened(List.of("s1"), "c1"));
    }

    @Test
    @DisplayName("notifyStaffNominated — logs without throwing")
    void notifyStaffNominated_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyStaffNominated("u1", "c1", "MANAGER"));
    }

    @Test
    @DisplayName("notifyStaffRemoved — logs without throwing")
    void notifyStaffRemoved_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyStaffRemoved("u1", "c1"));
    }

    @Test
    @DisplayName("notifyPermissionsUpdated — logs without throwing")
    void notifyPermissionsUpdated_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyPermissionsUpdated("u1", "c1"));
    }

    @Test
    @DisplayName("notifyCartExpired — logs without throwing")
    void notifyCartExpired_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyCartExpired("u1"));
    }

    @Test
    @DisplayName("notifyEventCancelled — logs for each buyer without throwing")
    void notifyEventCancelled_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyEventCancelled(List.of("b1", "b2"), "e1", "Concert"));
    }

    @Test
    @DisplayName("notifyRefundIssued — logs without throwing")
    void notifyRefundIssued_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyRefundIssued("u1", "r1", 50.0, "Concert"));
    }

    @Test
    @DisplayName("notifyEventRescheduled — logs for each buyer without throwing")
    void notifyEventRescheduled_doesNotThrow() {
        assertDoesNotThrow(() ->
                service.notifyEventRescheduled(List.of("b1"), "e1", "Concert", LocalDateTime.now().plusDays(1)));
    }

    @Test
    @DisplayName("notifyUserReactivated — logs without throwing")
    void notifyUserReactivated_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyUserReactivated("u1"));
    }

    @Test
    @DisplayName("notifyEventSoldOut — logs for each staff without throwing")
    void notifyEventSoldOut_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyEventSoldOut(List.of("s1"), "e1", "Concert"));
    }

    @Test
    @DisplayName("notifyRaffleWon — logs without throwing")
    void notifyRaffleWon_doesNotThrow() {
        assertDoesNotThrow(() ->
                service.notifyRaffleWon("u1", "e1", "AUTH-123", LocalDateTime.now().plusHours(1)));
    }

    @Test
    @DisplayName("notifyAdminMessage — logs without throwing")
    void notifyAdminMessage_doesNotThrow() {
        assertDoesNotThrow(() -> service.notifyAdminMessage("u1", "Hello!"));
    }
}
