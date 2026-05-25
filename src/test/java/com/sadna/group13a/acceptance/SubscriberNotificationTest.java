package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.EventListeners.NotificationEventListener;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.INotificationService;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.OrderService;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistoryItem;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.DomainServices.CheckoutDomainService;
import com.sadna.group13a.domain.DomainServices.TicketingAccessDomainService;
import com.sadna.group13a.domain.Events.OrderCompletedEvent;
import com.sadna.group13a.domain.Events.QueueTurnArrivedEvent;
import com.sadna.group13a.domain.Interfaces.IActiveOrderRepository;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IQueueRepository;
import com.sadna.group13a.domain.Interfaces.IRaffleRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.InMemoryNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for UC 2.6: Subscriber Receives Notifications.
 *
 * Verifies that business events (purchase confirmation, queue turn) are routed
 * correctly through the NotificationEventListener to the real INotificationService,
 * and that each notification reaches only its intended subscriber.
 *
 * Uses the real NotificationEventListener with a Mockito spy wrapping the
 * real InMemoryNotificationService so actual dispatch logic runs while
 * call signatures can be verified.
 */
@DisplayName("UC 2.6 — Subscriber Receives Notifications")
class SubscriberNotificationTest {

    private INotificationService notificationService;
    private NotificationEventListener notificationEventListener;

    @BeforeEach
    void setUp() {
        // Spy wraps the real InMemoryNotificationService so the actual dispatch
        // logic executes while Mockito can observe every call made to it.
        notificationService = spy(new InMemoryNotificationService());
        notificationEventListener = new NotificationEventListener(notificationService);
    }

    @Test
    @DisplayName("Given purchase completes — Then subscriber receives order-confirmation notification immediately")
    void GivenPurchaseCompletes_ThenOrderConfirmationNotificationDispatched() {
        String userId = "user1";
        String receiptId = "receipt-123";
        double totalPaid = 149.99;
        // Pre-condition: no purchase-confirmation notifications sent yet
        verify(notificationService, never()).notifyOrderCompleted(anyString(), anyString(), anyDouble());

        notificationEventListener.onOrderCompleted(new OrderCompletedEvent(receiptId, userId, totalPaid));

        // Post-condition: subscriber is notified with the correct receipt ID and amount
        verify(notificationService, times(1)).notifyOrderCompleted(userId, receiptId, totalPaid);
    }

    @Test
    @DisplayName("Given queue turn arrives — Then subscriber is notified immediately with correct purchase window")
    void GivenQueueTurnArrives_ThenSubscriberNotifiedWithCorrectWindow() {
        String userId = "user2";
        String eventId = "event-A";
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        // Pre-condition: no queue-turn notifications dispatched yet
        verify(notificationService, never()).notifyQueueTurnArrived(anyString(), anyString(), any());

        notificationEventListener.onQueueTurnArrived(new QueueTurnArrivedEvent(eventId, userId, expiresAt));

        // Post-condition: subscriber is notified with the event ID and their purchase-window deadline
        verify(notificationService, times(1)).notifyQueueTurnArrived(userId, eventId, expiresAt);
    }

    @Test
    @DisplayName("Given notification dispatched — Then it reaches only the intended subscriber, not others")
    void GivenNotificationDispatched_ThenReachesOnlyIntendedSubscriber() {
        String targetUser = "user1";
        String otherUser = "user2";
        String receiptId = "receipt-xyz";
        // Pre-condition: no order-completion notifications sent to either user
        verify(notificationService, never()).notifyOrderCompleted(anyString(), anyString(), anyDouble());

        notificationEventListener.onOrderCompleted(new OrderCompletedEvent(receiptId, targetUser, 50.00));

        // Post-condition: targetUser receives the notification; otherUser receives nothing
        verify(notificationService, times(1)).notifyOrderCompleted(targetUser, receiptId, 50.00);
        verify(notificationService, never()).notifyOrderCompleted(eq(otherUser), anyString(), anyDouble());
    }

    @Test
    @DisplayName("Given checkout succeeds — Then OrderCompletedEvent is published carrying the subscriber's ID and receipt")
    void GivenCheckoutSucceeds_ThenOrderCompletedEventPublishedWithCorrectSubscriberData() throws Exception {
        // Build a minimal OrderService with all infrastructure mocked and a
        // capturing ApplicationEventPublisher so we can inspect the published event.
        IActiveOrderRepository orderRepository      = mock(IActiveOrderRepository.class);
        IOrderHistoryRepository historyRepository   = mock(IOrderHistoryRepository.class);
        IEventRepository eventRepository            = mock(IEventRepository.class);
        ICompanyRepository companyRepository        = mock(ICompanyRepository.class);
        IQueueRepository queueRepository            = mock(IQueueRepository.class);
        IRaffleRepository raffleRepository          = mock(IRaffleRepository.class);
        IPaymentGateway paymentGateway              = mock(IPaymentGateway.class);
        ITicketSupplier ticketSupplier              = mock(ITicketSupplier.class);
        IUserRepository userRepository              = mock(IUserRepository.class);
        IAuth authGateway                           = mock(IAuth.class);
        CheckoutDomainService checkoutService       = mock(CheckoutDomainService.class);
        TicketingAccessDomainService accessService  = mock(TicketingAccessDomainService.class);
        ApplicationEventPublisher eventPublisher    = mock(ApplicationEventPublisher.class);

        OrderService orderService = new OrderService(
                orderRepository, historyRepository, eventRepository, companyRepository,
                queueRepository, raffleRepository, paymentGateway, ticketSupplier,
                userRepository, authGateway, checkoutService, accessService, eventPublisher);

        String token     = "tok";
        String userId    = "user1";
        String orderId   = "order1";
        String eventId   = "event1";
        String companyId = "comp1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(userId);
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(new Member(userId, "alice", "hash")));

        ActiveOrder order = new ActiveOrder(orderId, userId);
        order.addItem(new OrderItem(eventId, "zone1", "seat1", 100.0));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.getCompanyId()).thenReturn(companyId);
        when(event.getSaleMode()).thenReturn(EventSaleMode.REGULAR);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(mock(ProductionCompany.class)));

        OrderHistoryItem item = new OrderHistoryItem(
                eventId, "Title", LocalDateTime.now(), companyId, "Co", "Zone1", "Seat1", 100.0);
        when(checkoutService.checkoutItemsForEvent(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(item));
        when(paymentGateway.processPayment(100.0, "cc_good")).thenReturn(Result.success("txn_1"));
        when(ticketSupplier.issueTickets(any(), anyInt())).thenReturn(Result.success(List.of("ticket-1")));

        // Pre-condition: user is authenticated and the cart has items
        assertTrue(authGateway.validateToken(token), "Pre: user must be authenticated before checkout");
        assertEquals(1, order.getItems().size(), "Pre: cart must contain at least one item");

        Result<OrderHistoryDTO> result = orderService.executeCheckout(token, orderId, null, "cc_good");

        // Post-condition: checkout succeeds and the domain event carries the correct subscriber identity
        assertTrue(result.isSuccess(), "Post: checkout must succeed for a valid cart within timer");
        ArgumentCaptor<OrderCompletedEvent> captor = ArgumentCaptor.forClass(OrderCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(userId, captor.getValue().userId(),
                "Post: OrderCompletedEvent must reference the subscriber who completed the purchase");
        assertNotNull(captor.getValue().receiptId(),
                "Post: OrderCompletedEvent must carry a non-null receipt ID");
        assertEquals(100.0, captor.getValue().totalPaid(), 0.001,
                "Post: OrderCompletedEvent must carry the correct total paid amount");
    }
}
