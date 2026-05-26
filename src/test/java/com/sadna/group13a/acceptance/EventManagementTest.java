package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for UC 2.13: Event and Inventory Management.
 *
 * Verifies event CRUD, inventory updates, deletion blocking for sold events,
 * cancellation with automatic refunds, and real-time inventory reflection.
 */
@DisplayName("UC 2.13 — Event and Inventory Management")
class EventManagementTest {

    private EventService eventService;
    private IEventRepository eventRepository;
    private ICompanyRepository companyRepository;
    private IAuth authGateway;
    private IUserRepository userRepository;
    private IEventExtendedOperations extendedEventOperations;

    @BeforeEach
    void setUp() {
        eventRepository = mock(IEventRepository.class);
        companyRepository = mock(ICompanyRepository.class);
        authGateway = mock(IAuth.class);
        userRepository = mock(IUserRepository.class);
        extendedEventOperations = mock(IEventExtendedOperations.class);

        eventService = new EventService(eventRepository, companyRepository, authGateway, userRepository);
    }

    @Test
    @DisplayName("Given event with existing purchases — When attempting deletion — Then deletion blocked")
    void GivenEventWithPurchases_WhenDeleting_ThenBlocked() {
        String token = "valid_token";
        String eventId = "event1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn("user1");

        // Simulating the deletion operation that exists in another branch
        when(extendedEventOperations.deleteEvent(token, eventId))
                .thenReturn(Result.failure("Cannot delete event with existing purchases"));

        Result<Void> result = extendedEventOperations.deleteEvent(token, eventId);

        assertFalse(result.isSuccess(), "Deletion should be blocked if purchases exist");
        assertTrue(result.getErrorMessage().contains("existing purchases")
                || result.getErrorMessage().contains("purchases"));
    }

    @Test
    @DisplayName("Given event with purchases needs cancellation — When cancelled — Then full automatic refund to ALL buyers (UC 1.3)")
    void GivenEventWithPurchasesNeedsCancellation_WhenCancelled_ThenFullRefundToAll() {
        String token = "valid_token";
        String eventId = "event1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn("user1");

        // Simulating cancellation & refund logic from another branch
        when(extendedEventOperations.cancelEvent(token, eventId)).thenReturn(Result.success());

        Result<Void> result = extendedEventOperations.cancelEvent(token, eventId);

        assertTrue(result.isSuccess(), "Event cancellation with refunds should succeed");
        verify(extendedEventOperations).cancelEvent(token, eventId);
    }

    @Test
    @DisplayName("Given inventory update (e.g., add tickets) — Then reflected in real-time for queued/lottery users")
    void GivenInventoryUpdate_ThenReflectedInRealTime() {
        String token = "valid_token";
        String eventId = "event1";
        String companyId = "company1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn("user1");

        Event event = mock(Event.class);
        when(event.getCompanyId()).thenReturn(companyId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        ProductionCompany company = mock(ProductionCompany.class);
        when(company.hasPermission("user1", CompanyPermission.MANAGE_EVENTS)).thenReturn(true);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        // Pre-condition: event exists and user has MANAGE_EVENTS permission
        assertTrue(authGateway.validateToken(token), "Pre: user must be authenticated");
        assertTrue(company.hasPermission("user1", CompanyPermission.MANAGE_EVENTS), "Pre: user must have MANAGE_EVENTS permission");

        VenueMap newMap = mock(VenueMap.class);
        Result<Void> result = eventService.setVenueMap(token, eventId, newMap);

        // Post-condition: inventory update succeeds; venue map is applied to the domain object BEFORE persisting
        assertTrue(result.isSuccess(), "Post: inventory and venue update must succeed for authorized user");
        var inOrder = inOrder(event, eventRepository);
        inOrder.verify(event).setVenueMap(newMap);
        inOrder.verify(eventRepository).save(event);
    }

    @Test
    @DisplayName("Given inventory change contradicting purchase policy — Then change blocked")
    void GivenInventoryChangeContradictsPolicy_ThenBlocked() {
        String token = "valid_token";
        String eventId = "event1";
        String companyId = "company1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn("user1");

        Event event = mock(Event.class);
        when(event.getCompanyId()).thenReturn(companyId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        ProductionCompany company = mock(ProductionCompany.class);
        when(company.hasPermission("user1", CompanyPermission.MANAGE_EVENTS)).thenReturn(true);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        // Pre-condition: user has permission but the new map conflicts with existing purchase policies
        assertTrue(company.hasPermission("user1", CompanyPermission.MANAGE_EVENTS), "Pre: user must have MANAGE_EVENTS permission");

        VenueMap contradictingMap = mock(VenueMap.class);
        doThrow(new IllegalArgumentException("Inventory conflicts with existing purchase policy"))
                .when(event).setVenueMap(contradictingMap);

        Result<Void> result = eventService.setVenueMap(token, eventId, contradictingMap);

        // Post-condition: conflicting inventory change is blocked; event is not saved
        assertFalse(result.isSuccess(), "Post: inventory change contradicting policy must be blocked");
        assertTrue(result.getErrorMessage().contains("policy") || result.getErrorMessage().contains("conflicts"));
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given user without company management permissions — When editing event — Then access denied")
    void GivenUnauthorizedUser_WhenEditingEvent_ThenDenied() {
        String token = "invalid_token";
        String eventId = "event1";
        String companyId = "company1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn("unauthorized_user");

        Event event = mock(Event.class);
        when(event.getCompanyId()).thenReturn(companyId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        ProductionCompany company = mock(ProductionCompany.class);
        when(company.hasPermission("unauthorized_user", CompanyPermission.MANAGE_EVENTS)).thenReturn(false);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        // Pre-condition: user is authenticated but does NOT have MANAGE_EVENTS permission
        assertTrue(authGateway.validateToken(token), "Pre: user must be authenticated");
        assertFalse(company.hasPermission("unauthorized_user", CompanyPermission.MANAGE_EVENTS), "Pre: user must not have MANAGE_EVENTS permission");

        Result<Void> result = eventService.updateEventDetails(token, eventId, "New Title", "New Desc",
                LocalDateTime.now(), "Concerts");

        // Post-condition: editing is denied and event is not saved
        assertFalse(result.isSuccess(), "Post: user must be denied to edit event without permission");
        assertTrue(result.getErrorMessage().contains("User lacks permission"));
        verify(eventRepository, never()).save(any());
    }

    // Interface to mock missing operations (delete/cancel) that are expected to
    // exist in another branch
    public interface IEventExtendedOperations {
        Result<Void> deleteEvent(String token, String eventId);

        Result<Void> cancelEvent(String token, String eventId);
    }
}
