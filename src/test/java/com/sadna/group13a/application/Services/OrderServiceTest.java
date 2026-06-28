package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.OrderDTO;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistoryItem;
import com.sadna.group13a.domain.DomainServices.CartDomainService;
import com.sadna.group13a.domain.DomainServices.CheckoutDomainService;
import com.sadna.group13a.domain.DomainServices.TicketingAccessDomainService;
import com.sadna.group13a.domain.Events.OrderCompletedEvent;
import com.sadna.group13a.domain.Interfaces.IActiveOrderRepository;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IQueueRepository;
import com.sadna.group13a.domain.Interfaces.IRaffleRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private IActiveOrderRepository orderRepository;
    @Mock private IOrderHistoryRepository historyRepository;
    @Mock private IEventRepository eventRepository;
    @Mock private ICompanyRepository companyRepository;
    @Mock private IQueueRepository queueRepository;
    @Mock private IRaffleRepository raffleRepository;
    @Mock private IPaymentGateway paymentGateway;
    @Mock private ITicketSupplier ticketSupplier;
    @Mock private IUserRepository userRepository;
    @Mock private IAuth authGateway;
    @Mock private CheckoutDomainService checkoutDomainService;
    @Mock private TicketingAccessDomainService ticketingAccessDomainService;
    @Mock private CartDomainService cartDomainService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SystemLogService systemLogService;

    @InjectMocks
    private OrderService orderService;

    private static final String TOKEN      = "valid-token";
    private static final String USER_ID    = "user-1";
    private static final String EVENT_ID   = "event-1";
    private static final String COMPANY_ID = "co-1";
    private static final String ORDER_ID   = "order-1";

    private Event event;
    private ProductionCompany company;
    private ActiveOrder activeOrder;

    @BeforeEach
    void setUp() {
        Seat seat = new Seat("seat-1", "A-1");
        SeatedZone zone = new SeatedZone("zone-1", "VIP", 100.0, List.of(seat));
        VenueMap vm = new VenueMap("vm-1", "Arena");
        vm.addZone(zone);

        event = new Event(EVENT_ID, "Concert", "Desc", COMPANY_ID, LocalDateTime.now().plusDays(7), "Music");
        event.setVenueMap(vm);
        event.publish();

        company = new ProductionCompany(COMPANY_ID, "Acme", "Desc", "founder-1");

        activeOrder = new ActiveOrder(ORDER_ID, USER_ID);
        activeOrder.addItem(new OrderItem(EVENT_ID, "zone-1", "seat-1", 100.0));

        lenient().when(authGateway.validateToken(TOKEN)).thenReturn(true);
        lenient().when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new Member(USER_ID, "alice", "hash")));
        lenient().when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        lenient().when(paymentGateway.refundPayment(any())).thenReturn(Result.success());
    }

    // ── addItemToCart ─────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenAddItemToCart_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(orderService.addItemToCart("bad", EVENT_ID, "zone-1", "seat-1").isSuccess());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void givenEventNotFound_whenAddItemToCart_thenReturnsFailure() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertFalse(orderService.addItemToCart(TOKEN, EVENT_ID, "zone-1", "seat-1").isSuccess());
    }

    @Test
    void givenUnpublishedEvent_whenAddItemToCart_thenReturnsFailure() {
        Event unpublished = new Event(EVENT_ID, "Concert", "Desc", COMPANY_ID,
                LocalDateTime.now().plusDays(7), "Music");
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(unpublished));

        assertFalse(orderService.addItemToCart(TOKEN, EVENT_ID, "zone-1", "seat-1").isSuccess());
    }

    @Test
    void givenPublishedEventAndAvailableSeat_whenAddItemToCart_thenCartSaved() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(orderRepository.getOrCreate(eq(USER_ID), any()))
                .thenAnswer(inv -> inv.<java.util.function.Supplier<ActiveOrder>>getArgument(1).get());

        Result<String> result = orderService.addItemToCart(TOKEN, EVENT_ID, "zone-1", "seat-1");

        assertTrue(result.isSuccess());
        verify(orderRepository).save(any(ActiveOrder.class));
        verify(eventRepository).save(event);
    }

    // ── viewCart ──────────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenViewCart_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(orderService.viewCart("bad").isSuccess());
    }

    @Test
    void givenNoActiveCart_whenViewCart_thenReturnsFailure() {
        when(orderRepository.findActiveByUserId(USER_ID)).thenReturn(Optional.empty());

        assertFalse(orderService.viewCart(TOKEN).isSuccess());
    }

    @Test
    void givenActiveCart_whenViewCart_thenReturnsOrderDto() {
        when(orderRepository.findActiveByUserId(USER_ID)).thenReturn(Optional.of(activeOrder));

        Result<OrderDTO> result = orderService.viewCart(TOKEN);

        assertTrue(result.isSuccess());
        assertEquals(ORDER_ID, result.getData().get().orderId());
    }

    // ── cancelCart ────────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenCancelCart_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(orderService.cancelCart("bad").isSuccess());
    }

    @Test
    void givenNoActiveCart_whenCancelCart_thenSucceedsGracefully() {
        when(orderRepository.findActiveByUserId(USER_ID)).thenReturn(Optional.empty());

        assertTrue(orderService.cancelCart(TOKEN).isSuccess());
        verify(orderRepository, never()).deleteById(any());
    }

    @Test
    void givenActiveCart_whenCancelCart_thenCartDeleted() {
        when(orderRepository.findActiveByUserId(USER_ID)).thenReturn(Optional.of(activeOrder));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        Result<Void> result = orderService.cancelCart(TOKEN);

        assertTrue(result.isSuccess());
        verify(orderRepository).deleteById(ORDER_ID);
    }

    // ── executeCheckout ───────────────────────────────────────────

    @Test
    void givenInvalidToken_whenExecuteCheckout_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(orderService.executeCheckout("bad", ORDER_ID, null, null, "card").isSuccess());
    }

    @Test
    void givenCartNotFound_whenExecuteCheckout_thenReturnsFailure() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertFalse(orderService.executeCheckout(TOKEN, ORDER_ID, null, null, "card").isSuccess());
    }

    @Test
    void givenCartBelongingToOtherUser_whenExecuteCheckout_thenReturnsFailure() {
        ActiveOrder otherOrder = new ActiveOrder(ORDER_ID, "other-user");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(otherOrder));

        assertFalse(orderService.executeCheckout(TOKEN, ORDER_ID, null, null, "card").isSuccess());
    }

    @Test
    void givenEmptyCart_whenExecuteCheckout_thenReturnsFailure() {
        ActiveOrder emptyOrder = new ActiveOrder(ORDER_ID, USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(emptyOrder));

        assertFalse(orderService.executeCheckout(TOKEN, ORDER_ID, null, null, "card").isSuccess());
    }

    @Test
    void givenPaymentDeclined_whenExecuteCheckout_thenReturnsFailure() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(activeOrder));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(checkoutDomainService.checkoutItemsForEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildHistoryItems());
        when(paymentGateway.processPayment(anyDouble(), anyString()))
                .thenReturn(Result.failure("Declined"));

        Result<OrderHistoryDTO> result = orderService.executeCheckout(TOKEN, ORDER_ID, null, null, "card");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Payment declined"));
        verify(historyRepository, never()).save(any());
    }

    @Test
    void givenSuccessfulCheckout_whenExecuteCheckout_thenHistorySavedAndEventPublished() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(activeOrder));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(checkoutDomainService.checkoutItemsForEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildHistoryItems());
        when(paymentGateway.processPayment(anyDouble(), anyString()))
                .thenReturn(Result.success("TXN-123"));
        when(ticketSupplier.issueTickets(anyString(), any()))
                .thenReturn(Result.success(List.of("TICKET-001")));

        Result<OrderHistoryDTO> result = orderService.executeCheckout(TOKEN, ORDER_ID, null, null, "card");

        assertTrue(result.isSuccess());
        verify(historyRepository).save(any(OrderHistory.class));
        verify(orderRepository).deleteById(ORDER_ID);
        verify(eventPublisher).publishEvent(any(OrderCompletedEvent.class));
    }

    @Test
    void givenTicketIssuanceFails_whenExecuteCheckout_thenPaymentRefundedAndReturnsFailure() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(activeOrder));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(checkoutDomainService.checkoutItemsForEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildHistoryItems());
        when(paymentGateway.processPayment(anyDouble(), anyString()))
                .thenReturn(Result.success("TXN-123"));
        when(ticketSupplier.issueTickets(anyString(), any()))
                .thenReturn(Result.failure("Ticket service unavailable"));

        Result<OrderHistoryDTO> result = orderService.executeCheckout(TOKEN, ORDER_ID, null, null, "card");

        assertFalse(result.isSuccess());
        verify(paymentGateway).refundPayment("TXN-123");
        verify(historyRepository, never()).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────

    private List<OrderHistoryItem> buildHistoryItems() {
        return List.of(new OrderHistoryItem(
                EVENT_ID, "Concert", LocalDateTime.now().plusDays(7),
                COMPANY_ID, "Acme", "VIP", "A-1", 100.0));
    }

}
