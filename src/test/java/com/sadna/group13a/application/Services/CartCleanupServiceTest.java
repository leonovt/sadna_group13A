package com.sadna.group13a.application.Services;

import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Interfaces.IActiveOrderRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * Unit tests for CartCleanupService.expireStaleOrders().
 *
 * All tests use Mockito to isolate the service from infrastructure.
 */
class CartCleanupServiceTest {

    private IActiveOrderRepository orderRepository;
    private IEventRepository eventRepository;
    private CartCleanupService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(IActiveOrderRepository.class);
        eventRepository = mock(IEventRepository.class);
        service = new CartCleanupService(orderRepository, eventRepository, mock(ApplicationEventPublisher.class));
    }

    @Test
    void givenNoOrders_whenCleanupRuns_thenNothingIsDeleted() {
        when(orderRepository.findAll()).thenReturn(List.of());

        service.expireStaleOrders();

        verify(orderRepository, never()).deleteById(anyString());
    }

    @Test
    void givenOnlyFreshOrders_whenCleanupRuns_thenNothingIsDeleted() {
        ActiveOrder fresh = mock(ActiveOrder.class);
        when(fresh.isExpired()).thenReturn(false);
        when(orderRepository.findAll()).thenReturn(List.of(fresh));

        service.expireStaleOrders();

        verify(orderRepository, never()).deleteById(anyString());
        verify(eventRepository, never()).findById(anyString());
    }

    @Test
    void givenExpiredOrderWithNoItems_whenCleanupRuns_thenOrderDeleted() {
        ActiveOrder expired = mock(ActiveOrder.class);
        when(expired.isExpired()).thenReturn(true);
        when(expired.getId()).thenReturn("order-1");
        when(expired.getUserId()).thenReturn("user-1");
        when(expired.getItems()).thenReturn(List.of());
        when(orderRepository.findAll()).thenReturn(List.of(expired));

        service.expireStaleOrders();

        verify(orderRepository).deleteById("order-1");
        verify(eventRepository, never()).findById(anyString());
    }

    @Test
    void givenExpiredOrderWithItem_whenEventFound_thenHoldReleasedAndOrderDeleted() {
        OrderItem cartItem = new OrderItem("event-1", "zone-1", "seat-1", 50.0);
        ActiveOrder expired = mock(ActiveOrder.class);
        when(expired.isExpired()).thenReturn(true);
        when(expired.getId()).thenReturn("order-1");
        when(expired.getUserId()).thenReturn("user-1");
        when(expired.getItems()).thenReturn(List.of(cartItem));
        when(orderRepository.findAll()).thenReturn(List.of(expired));

        Event event = mock(Event.class);
        when(eventRepository.findById("event-1")).thenReturn(Optional.of(event));

        service.expireStaleOrders();

        verify(event).releaseItem("zone-1", "seat-1", "user-1");
        verify(eventRepository).save(event);
        verify(orderRepository).deleteById("order-1");
    }

    @Test
    void givenExpiredOrderWithItem_whenEventNotFound_thenOrderStillDeleted() {
        OrderItem cartItem = new OrderItem("event-x", "zone-1", "seat-1", 50.0);
        ActiveOrder expired = mock(ActiveOrder.class);
        when(expired.isExpired()).thenReturn(true);
        when(expired.getId()).thenReturn("order-2");
        when(expired.getUserId()).thenReturn("user-2");
        when(expired.getItems()).thenReturn(List.of(cartItem));
        when(orderRepository.findAll()).thenReturn(List.of(expired));

        when(eventRepository.findById("event-x")).thenReturn(Optional.empty());

        service.expireStaleOrders();

        verify(eventRepository, never()).save(any());
        verify(orderRepository).deleteById("order-2");
    }

    @Test
    void givenExpiredOrderWhereReleaseFails_whenCleanupRuns_thenOrderStillDeleted() {
        OrderItem cartItem = new OrderItem("event-1", "zone-1", "seat-1", 50.0);
        ActiveOrder expired = mock(ActiveOrder.class);
        when(expired.isExpired()).thenReturn(true);
        when(expired.getId()).thenReturn("order-3");
        when(expired.getUserId()).thenReturn("user-3");
        when(expired.getItems()).thenReturn(List.of(cartItem));
        when(orderRepository.findAll()).thenReturn(List.of(expired));

        Event event = mock(Event.class);
        when(eventRepository.findById("event-1")).thenReturn(Optional.of(event));
        doThrow(new RuntimeException("seat already free")).when(event).releaseItem(any(), any(), any());

        service.expireStaleOrders();

        verify(orderRepository).deleteById("order-3");
    }

    @Test
    void givenMixOfExpiredAndFreshOrders_whenCleanupRuns_thenOnlyExpiredDeleted() {
        ActiveOrder fresh = mock(ActiveOrder.class);
        when(fresh.isExpired()).thenReturn(false);

        ActiveOrder expired = mock(ActiveOrder.class);
        when(expired.isExpired()).thenReturn(true);
        when(expired.getId()).thenReturn("stale-1");
        when(expired.getUserId()).thenReturn("user-1");
        when(expired.getItems()).thenReturn(List.of());
        when(orderRepository.findAll()).thenReturn(List.of(fresh, expired));

        service.expireStaleOrders();

        verify(orderRepository).deleteById("stale-1");
        verify(orderRepository, times(1)).deleteById(anyString());
    }
}
