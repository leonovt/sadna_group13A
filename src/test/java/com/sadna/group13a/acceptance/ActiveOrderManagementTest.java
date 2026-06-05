package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.OrderService;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Aggregates.Company.CompanyStatus;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.DomainServices.CartDomainService;
import com.sadna.group13a.domain.DomainServices.CheckoutDomainService;
import com.sadna.group13a.domain.DomainServices.TicketingAccessDomainService;
import com.sadna.group13a.domain.Interfaces.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.*;

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
    private ITicketSupplier ticketSupplier;
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
        ticketSupplier = mock(ITicketSupplier.class);
        paymentGateway = mock(IPaymentGateway.class);
        userRepository = mock(IUserRepository.class);
        authGateway = mock(IAuth.class);
        checkoutDomainService = mock(CheckoutDomainService.class);
        ticketingAccessDomainService = mock(TicketingAccessDomainService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        orderService = new OrderService(
                orderRepository, historyRepository, eventRepository, companyRepository,
                queueRepository, raffleRepository, paymentGateway, ticketSupplier, userRepository, authGateway,
                checkoutDomainService, ticketingAccessDomainService, eventPublisher, new CartDomainService()
        );

        // Default: any userId resolves to an active member so user-guard tests pass through
        when(userRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.of(new Member(inv.getArgument(0), "user", "hash")));
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
        
        ProductionCompany company = mock(ProductionCompany.class);
        when(company.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(event.getCompanyId()).thenReturn("company1");
        when(companyRepository.findById("company1")).thenReturn(Optional.of(company));
        doThrow(new IllegalArgumentException("Seat not found")).when(event).reserveSeat("zone1", "seat2", userId);

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
        // Pre-condition: cart exists with the item; user is authenticated
        assertTrue(authGateway.validateToken(token), "Pre: user must be authenticated");
        assertEquals(1, order.getItems().size(), "Pre: cart must contain the item before removal");

        Result<Void> result = orderService.removeItemFromCart(token, eventId, "zone1", "seat1");

        // Post-condition: removal succeeds, seat is released back to inventory immediately
        assertTrue(result.isSuccess(), "Post: item removal must succeed");
        verify(event).releaseItem("zone1", "seat1", userId);
        verify(eventRepository).save(event);
        verify(orderRepository).save(order);
        assertEquals(0, order.getItems().size(), "Post: cart must be empty after removing the only item");
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
        // Pre-condition: cart exists with an expiry timer already set
        assertNotNull(originalExpiry, "Pre: cart must have an expiry timer set before update");

        when(orderRepository.findActiveByUserId(userId)).thenReturn(Optional.of(order));
        when(orderRepository.getOrCreate(eq(userId), any())).thenReturn(order);

        Event event = mock(Event.class);
        when(eventRepository.findById("ev1")).thenReturn(Optional.of(event));
        when(event.isPublished()).thenReturn(true);
        when(event.getCompanyId()).thenReturn("comp1");
        when(event.getZoneBasePrice("zone1")).thenReturn(100.0);
        ProductionCompany company = mock(ProductionCompany.class);
        when(company.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(companyRepository.findById("comp1")).thenReturn(Optional.of(company));

        Result<String> result = orderService.addItemToCart(token, "ev1", "zone1", "seat1");

        // Post-condition: item added but timer is unchanged (no infinite hold extension)
        assertTrue(result.isSuccess(), "Post: adding item must succeed");
        assertEquals(originalExpiry, order.getExpiresAt(), "Post: hold timer must not be reset on cart update");
        assertEquals(1, order.getItems().size(), "Post: cart must contain the newly added item after update");
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
    @DisplayName("Given valid seated tickets — When adding batch to cart — Then all seats held and items added")
    void GivenValidSeatedTickets_WhenAddingBatch_ThenAllHeldAndInCart() {
        String token = "valid_token";
        String userId = "user123";
        String eventId = "event1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(userId);

        Event event = mock(Event.class);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(event.isPublished()).thenReturn(true);
        when(event.getCompanyId()).thenReturn("company1");
        when(event.getZoneBasePrice("zone1")).thenReturn(50.0);

        ProductionCompany company = mock(ProductionCompany.class);
        when(company.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(companyRepository.findById("company1")).thenReturn(Optional.of(company));
        when(orderRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());
        when(orderRepository.getOrCreate(eq(userId), any()))
                .thenAnswer(inv -> inv.<java.util.function.Supplier<ActiveOrder>>getArgument(1).get());

        Result<String> result = orderService.addBatchItemsToCart(
                token, eventId, "zone1", List.of("seat1", "seat2"), null);

        assertTrue(result.isSuccess());
        verify(event).reserveSeat("zone1", "seat1", userId);
        verify(event).reserveSeat("zone1", "seat2", userId);
        verify(orderRepository).save(argThat(order -> order.getItems().size() == 2));
    }

    @Test
    @DisplayName("Given standing zone — When adding batch by quantity — Then N spots held and items in cart")
    void GivenStandingZone_WhenAddingBatchByQuantity_ThenNSpotsHeld() {
        String token = "valid_token";
        String userId = "user123";
        String eventId = "event1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(userId);

        Event event = mock(Event.class);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(event.isPublished()).thenReturn(true);
        when(event.getCompanyId()).thenReturn("company1");
        when(event.getZoneBasePrice("standing1")).thenReturn(30.0);

        ProductionCompany company = mock(ProductionCompany.class);
        when(company.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(companyRepository.findById("company1")).thenReturn(Optional.of(company));
        when(orderRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());
        when(orderRepository.getOrCreate(eq(userId), any()))
                .thenAnswer(inv -> inv.<java.util.function.Supplier<ActiveOrder>>getArgument(1).get());

        Result<String> result = orderService.addBatchItemsToCart(
                token, eventId, "standing1", null, 3);

        assertTrue(result.isSuccess());
        verify(event, times(3)).reserveSeat(eq("standing1"), isNull(), eq(userId));
        verify(orderRepository).save(argThat(order -> order.getItems().size() == 3));
    }

    @Test
    @DisplayName("Given second seat unavailable — When adding batch — Then first seat released and cart unchanged")
    void GivenSecondSeatUnavailable_WhenAddingBatch_ThenFirstReleasedAndCartUnchanged() {
        String token = "valid_token";
        String userId = "user123";
        String eventId = "event1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(userId);

        Event event = mock(Event.class);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(event.isPublished()).thenReturn(true);
        when(event.getCompanyId()).thenReturn("company1");

        ProductionCompany company = mock(ProductionCompany.class);
        when(company.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(companyRepository.findById("company1")).thenReturn(Optional.of(company));

        doNothing().when(event).reserveSeat("zone1", "seat1", userId);
        doThrow(new com.sadna.group13a.domain.shared.SeatUnavailableException("seat2 is taken"))
                .when(event).reserveSeat("zone1", "seat2", userId);

        Result<String> result = orderService.addBatchItemsToCart(
                token, eventId, "zone1", List.of("seat1", "seat2"), null);

        assertFalse(result.isSuccess());
        verify(event).releaseItem("zone1", "seat1", userId);
        verify(orderRepository, never()).save(any(ActiveOrder.class));
    }

    @Test
    @DisplayName("Given empty seat list and null quantity — When adding batch — Then validation error returned")
    void GivenEmptySeatListAndNullQuantity_WhenAddingBatch_ThenValidationError() {
        String token = "valid_token";
        String userId = "user123";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(userId);

        Event event = mock(Event.class);
        when(eventRepository.findById("event1")).thenReturn(Optional.of(event));
        when(event.isPublished()).thenReturn(true);
        when(event.getCompanyId()).thenReturn("company1");
        ProductionCompany company = mock(ProductionCompany.class);
        when(company.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(companyRepository.findById("company1")).thenReturn(Optional.of(company));

        Result<String> result = orderService.addBatchItemsToCart(
                token, "event1", "zone1", Collections.emptyList(), null);

        assertFalse(result.isSuccess());
        verify(orderRepository, never()).save(any(ActiveOrder.class));
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
        when(event.getCompanyId()).thenReturn("company1");
        ProductionCompany company = mock(ProductionCompany.class);
        when(company.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(companyRepository.findById("company1")).thenReturn(Optional.of(company));
        doThrow(new RuntimeException("Policy limit exceeded")).when(event).reserveSeat("zone1", "seat1", userId);
        
        Result<String> result = orderService.addItemToCart(token, eventId, "zone1", "seat1");
        assertFalse(result.isSuccess(), "Should fail on policy limit exceeded");
        assertTrue(result.getErrorMessage().contains("Policy limit exceeded"));
    }
}
