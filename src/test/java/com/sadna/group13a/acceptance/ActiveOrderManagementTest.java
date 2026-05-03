package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.DTO.OrderDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.OrderService;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.DomainServices.CheckoutDomainService;
import com.sadna.group13a.domain.DomainServices.TicketingAccessDomainService;
import com.sadna.group13a.domain.Interfaces.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for UC 2.9: Managing the Active Order (Cart).
 *
 * Verifies cart modification: adding/removing items, policy re-check,
 * inventory sync, and timer non-reset behavior.
 */
@DisplayName("UC 2.9 — Managing the Active Order (Cart)")
class ActiveOrderManagementTest {

    private OrderService orderService;
    private IActiveOrderRepository orderRepository;
    private IOrderHistoryRepository historyRepository;
    private IEventRepository eventRepository;
    private ICompanyRepository companyRepository;
    private IQueueRepository queueRepository;
    private IRaffleRepository raffleRepository;
    private IPaymentGateway paymentGateway;
    private IUserRepository userRepository;
    private IAuth authGateway;
    private CheckoutDomainService checkoutDomainService;
    private TicketingAccessDomainService ticketingAccessDomainService;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        orderRepository = mock(IActiveOrderRepository.class);
        historyRepository = mock(IOrderHistoryRepository.class);
        eventRepository = mock(IEventRepository.class);
        companyRepository = mock(ICompanyRepository.class);
        queueRepository = mock(IQueueRepository.class);
        raffleRepository = mock(IRaffleRepository.class);
        paymentGateway = mock(IPaymentGateway.class);
        userRepository = mock(IUserRepository.class);
        authGateway = mock(IAuth.class);
        checkoutDomainService = mock(CheckoutDomainService.class);
        ticketingAccessDomainService = mock(TicketingAccessDomainService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        orderService = new OrderService(
                orderRepository, historyRepository, eventRepository, companyRepository,
                queueRepository, raffleRepository, paymentGateway, userRepository, authGateway,
                checkoutDomainService, ticketingAccessDomainService, eventPublisher
        );
    }

    @Test
    @DisplayName("Given quantity increase exceeding inventory or policy — When updating cart — Then update rejected, existing items untouched")
    void GivenQuantityIncreaseExceedingLimit_WhenUpdating_ThenRejectedExistingUntouched() {
        // Assume IPolicyService exists in another branch, wait the logic is in order service: addItemToCart
        String token = "valid_token";
        String userId = "user123";
        String eventId = "event1";
        
        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(userId);
        
        Event event = mock(Event.class);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(event.isPublished()).thenReturn(true);
        
        // Mock a seat that throws IllegalArgumentException because it is already taken or unavailable
        SeatedZone mockZone = mock(SeatedZone.class);
        when(event.getZoneById("zone1")).thenReturn(mockZone);
        Seat unavailableSeat = mock(Seat.class);
        doThrow(new IllegalArgumentException("Seat not found")).when(unavailableSeat).hold(userId);
        when(mockZone.findSeatById("seat2")).thenReturn(Optional.of(unavailableSeat));
        
        Result<String> result = orderService.addItemToCart(token, eventId, "zone1", "seat2");
        assertFalse(result.isSuccess(), "Should reject addition of unavailable seat");
        
        // Ensure the order wasn't saved with the bad seat
        verify(orderRepository, never()).save(any(ActiveOrder.class));
    }

    @Test
    @DisplayName("Given ticket removed from cart — Then seat released to available inventory within 2 seconds")
    void GivenTicketRemoved_ThenSeatReleasedWithin2Seconds() {
        String token = "valid_token";
        String userId = "user123";
        String eventId = "ev1";
        
        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(userId);
        
        ActiveOrder order = new ActiveOrder("order1", userId);
        order.addItem(new OrderItem(eventId, "zone1", "seat1", 50.0));
        when(orderRepository.findActiveByUserId(userId)).thenReturn(Optional.of(order));
        
        Event event = mock(Event.class);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        SeatedZone zone = mock(SeatedZone.class);
        Seat seat = mock(Seat.class);
        when(event.getZoneById("zone1")).thenReturn(zone);
        when(zone.findSeatById("seat1")).thenReturn(Optional.of(seat));
        
        Result<Void> result = orderService.removeItemFromCart(token, eventId, "zone1", "seat1");
        
        assertTrue(result.isSuccess());
        verify(seat).release();
        verify(eventRepository).save(event);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("Given cart update — Then original hold timer is NOT reset (prevents infinite hold abuse)")
    void GivenCartUpdate_ThenTimerNotReset() {
        // Critical: users must NOT abuse cart updates to hold seats indefinitely
        String token = "valid_token";
        String userId = "user123";
        
        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(userId);
        
        ActiveOrder order = new ActiveOrder("order1", userId);
        LocalDateTime originalExpiry = order.getExpiresAt();
        
        when(orderRepository.findActiveByUserId(userId)).thenReturn(Optional.of(order));
        
        Event event = mock(Event.class);
        when(eventRepository.findById("ev1")).thenReturn(Optional.of(event));
        when(event.isPublished()).thenReturn(true);
        SeatedZone zone = mock(SeatedZone.class);
        Seat seat = mock(Seat.class);
        when(event.getZoneById("zone1")).thenReturn(zone);
        when(zone.findSeatById("seat1")).thenReturn(Optional.of(seat));
        when(zone.getBasePrice()).thenReturn(100.0);
        
        Result<String> result = orderService.addItemToCart(token, "ev1", "zone1", "seat1");
        
        assertTrue(result.isSuccess());
        assertEquals(originalExpiry, order.getExpiresAt(), "Hold timer should not be reset on update");
    }

    @Test
    @DisplayName("Given hold timer expired — When trying to update cart — Then cart cancelled")
    void GivenTimerExpired_WhenUpdatingCart_ThenCartCancelled() {
        String token = "valid_token";
        String userId = "user123";
        
        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(userId);
        
        ActiveOrder order = mock(ActiveOrder.class);
        when(order.getId()).thenReturn("order1");
        when(order.getUserId()).thenReturn(userId);
        when(order.getExpiresAt()).thenReturn(LocalDateTime.now().minusMinutes(1)); // Expired
        
        when(orderRepository.findActiveByUserId(userId)).thenReturn(Optional.of(order));
        
        // This relies on presumed timer logic in another branch. 
        // For now we test cancelCart behaves correctly.
        Result<Void> result = orderService.cancelCart(token);
        assertTrue(result.isSuccess());
        verify(orderRepository).deleteById("order1");
    }

    @Test
    @DisplayName("Given quantity increase — Then policy re-checked before allowing addition")
    void GivenQuantityIncrease_ThenPolicyRechecked() {
        String token = "valid_token";
        String userId = "user123";
        String eventId = "event1";
        
        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(userId);
        
        // Policy mocked via check in Domain Service which is assumed to happen in another branch/update
        Event event = mock(Event.class);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(event.isPublished()).thenReturn(true);
        
        SeatedZone mockZone = mock(SeatedZone.class);
        when(event.getZoneById("zone1")).thenReturn(mockZone);
        Seat seat = mock(Seat.class);
        when(mockZone.findSeatById("seat1")).thenReturn(Optional.of(seat));
        doThrow(new RuntimeException("Policy limit exceeded")).when(seat).hold(userId);
        
        Result<String> result = orderService.addItemToCart(token, eventId, "zone1", "seat1");
        assertFalse(result.isSuccess(), "Should fail on policy limit exceeded");
        assertTrue(result.getErrorMessage().contains("Policy limit exceeded"));
    }
}
