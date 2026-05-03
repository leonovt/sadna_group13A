package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.DTO.OrderDTO;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.OrderHistoryItemDTO;
import com.sadna.group13a.application.DTO.OrderItemDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.StandingZone;
import com.sadna.group13a.domain.Aggregates.Event.Zone;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.Raffle.AuthorizationCode;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
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
import com.sadna.group13a.domain.shared.PermissionDeniedException;
import com.sadna.group13a.domain.shared.SeatUnavailableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Application service for managing shopping carts and checkout.
 * Implements UC 1.6 (Reserve Seats), UC 1.8 (Checkout Cart).
 * Orchestrates repositories and domain services; contains no business logic itself.
 */
@Service
public class OrderService {

    private final IUserRepository userRepository;
    private final IActiveOrderRepository orderRepository;
    private final IOrderHistoryRepository historyRepository;
    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IQueueRepository queueRepository;
    private final IRaffleRepository raffleRepository;
    private final IPaymentGateway paymentGateway;
    private final IAuth authGateway;
    private final CheckoutDomainService checkoutDomainService;
    private final TicketingAccessDomainService ticketingAccessDomainService;
    private final ApplicationEventPublisher eventPublisher;

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private final ConcurrentHashMap<String, Object> eventLocks = new ConcurrentHashMap<>();

    public OrderService(
            IActiveOrderRepository orderRepository,
            IOrderHistoryRepository historyRepository,
            IEventRepository eventRepository,
            ICompanyRepository companyRepository,
            IQueueRepository queueRepository,
            IRaffleRepository raffleRepository,
            IPaymentGateway paymentGateway,
            IUserRepository userRepository,
            IAuth authGateway,
            CheckoutDomainService checkoutDomainService,
            TicketingAccessDomainService ticketingAccessDomainService,
            ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.queueRepository = queueRepository;
        this.raffleRepository = raffleRepository;
        this.paymentGateway = paymentGateway;
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.checkoutDomainService = checkoutDomainService;
        this.ticketingAccessDomainService = ticketingAccessDomainService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Adds an item to the user's active cart.  If no cart exists, creates one.
     * Holds the seat/spot immediately to prevent double-booking.
     */
    public Result<String> addItemToCart(String token, String eventId, String zoneId, String seatId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to add item to cart");
            return Result.failure("Unauthorized: invalid token");
        }
        String userId = authGateway.extractUserId(token);

        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found");

        Event event = eventOpt.get();
        if (!event.isPublished()) return Result.failure("Event is not published");

        try {
            Zone zone = event.getZoneById(zoneId);
            if (zone instanceof SeatedZone sz) {
                sz.findSeatById(seatId)
                        .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId))
                        .hold(userId);
            } else if (zone instanceof StandingZone stz) {
                stz.holdStandingSpot(userId);
            }
            eventRepository.save(event);
        } catch (Exception e) {
            logger.warn("Failed to reserve seat {} in zone {} for user {}: {}", seatId, zoneId, userId, e.getMessage());
            return Result.failure("Failed to reserve seat: " + e.getMessage());
        }

        double price = event.getZoneById(zoneId).getBasePrice();
        ActiveOrder order = orderRepository.findActiveByUserId(userId)
                .orElseGet(() -> new ActiveOrder(UUID.randomUUID().toString(), userId));

        order.addItem(new OrderItem(eventId, zoneId, seatId, price));
        orderRepository.save(order);

        logger.info("User {} added item to cart {}", userId, order.getId());
        return Result.success(order.getId());
    }

    /**
     * Full checkout flow: validates access rights, executes domain checkout,
     * charges payment, and persists the finalized receipt.
     *
     * @param token           authenticated user token
     * @param activeOrderId   the cart to check out
     * @param optionalAuthCode raffle authorization code (null for REGULAR/QUEUE events)
     * @param paymentDetails  payment instrument data passed to the payment gateway
     */
    public Result<OrderHistoryDTO> executeCheckout(
            String token,
            String activeOrderId,
            String optionalAuthCode,
            String paymentDetails) {

        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized checkout attempt for order {}", activeOrderId);
            return Result.failure("Unauthorized: invalid token");
        }
        String userId = authGateway.extractUserId(token);

        // ── Fetch aggregates ──────────────────────────────────────────────────────
        Optional<ActiveOrder> orderOpt = orderRepository.findById(activeOrderId);
        if (orderOpt.isEmpty()) return Result.failure("Cart not found");
        ActiveOrder order = orderOpt.get();

        if (!order.getUserId().equals(userId)) {
            logger.warn("User {} attempted to check out cart belonging to {}", userId, order.getUserId());
            return Result.failure("Unauthorized: this cart does not belong to you");
        }

        if (order.getItems().isEmpty()) return Result.failure("Cart is empty");

        // V1: single event per cart — take the event from the first item
        String eventId = order.getItems().get(0).getEventId();
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found");
        Event event = eventOpt.get();

        Optional<ProductionCompany> companyOpt = companyRepository.findById(event.getCompanyId());
        if (companyOpt.isEmpty()) return Result.failure("Company not found");
        ProductionCompany company = companyOpt.get();

        // ── Resolve access-check inputs based on sale mode ────────────────────────
        TicketQueue queue = null;
        AuthorizationCode authCode = null;

        if (event.getSaleMode() == EventSaleMode.QUEUE) {
            queue = queueRepository.findByEventId(eventId).orElse(null);
        } else if (event.getSaleMode() == EventSaleMode.RAFFLE) {
            authCode = raffleRepository.findByEventId(eventId).stream()
                    .findFirst()
                    .flatMap(r -> r.getAuthorizationCodeFor(userId))
                    .filter(c -> c.isValidFor(userId, eventId))
                    .orElse(null);
        }

        // ── Access gate ───────────────────────────────────────────────────────────
        try {
            ticketingAccessDomainService.validateAccess(event, userId, queue, authCode);
        } catch (PermissionDeniedException e) {
            logger.warn("Access denied for user {} on event {}: {}", userId, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }

        // ── Domain checkout (mutates seat state on the in-memory Event aggregate) ─
        // Per-event lock prevents two threads from concurrently booking the same seat.
        Object eventLock = eventLocks.computeIfAbsent(eventId, k -> new Object());
        OrderHistory history;
        synchronized (eventLock) {
            try {
                history = checkoutDomainService.checkout(order, event, company, null, null);
            } catch (SeatUnavailableException e) {
                logger.warn("Seat unavailable during checkout for user {}: {}", userId, e.getMessage());
                return Result.failure("Seat no longer available: " + e.getMessage());
            } catch (Exception e) {
                logger.error("Checkout domain logic failed for user {}: {}", userId, e.getMessage());
                return Result.failure("Checkout failed: " + e.getMessage());
            }
        }

        // ── Payment ───────────────────────────────────────────────────────────────
        Result<String> paymentResult = paymentGateway.processPayment(history.getTotalPaid(), paymentDetails);
        if (!paymentResult.isSuccess()) {
            logger.warn("Payment declined for user {} (amount {}): {}", userId, history.getTotalPaid(), paymentResult.getErrorMessage());
            return Result.failure("Payment declined: " + paymentResult.getErrorMessage());
        }
        String transactionId = paymentResult.getOrThrow();

        // ── Persist ───────────────────────────────────────────────────────────────
        // eventRepository.save() would throw OptimisticLockException here when JPA is in use.
        // For V1 (ConcurrentHashMap) this never fires, but the catch is structural scaffolding.
        try {
            eventRepository.save(event);
        } catch (Exception e) {
            // Seat map could not be persisted — refund and abort to keep system consistent
            logger.error("Failed to persist seat map after payment for order {}: {}", activeOrderId, e.getMessage());
            paymentGateway.refundPayment(transactionId);
            return Result.failure("Concurrent seat modification detected — please retry. Your payment has been refunded.");
        }

        historyRepository.save(history);
        orderRepository.deleteById(activeOrderId);

        // Remove the user from the queue's active slot once checkout completes
        if (queue != null) {
            queue.removeActiveUser(userId);
            queueRepository.save(queue);
        }

        eventPublisher.publishEvent(new OrderCompletedEvent(history.getReceiptId(), userId, history.getTotalPaid()));
        logger.info("Checkout complete for user {} — receipt {}, transaction {}", userId, history.getReceiptId(), transactionId);

        // ── Map to DTO ────────────────────────────────────────────────────────────
        List<OrderHistoryItemDTO> itemDTOs = history.getItems().stream()
                .map(i -> new OrderHistoryItemDTO(
                        i.getEventId(),
                        i.getEventTitle(),
                        i.getEventDate(),
                        i.getCompanyName(),
                        i.getZoneName(),
                        i.getSeatLabel(),
                        i.getPricePaid()))
                .collect(Collectors.toList());

        return Result.success(new OrderHistoryDTO(
                history.getReceiptId(),
                history.getUserId(),
                history.getPurchaseDate(),
                history.getTotalPaid(),
                itemDTOs));
    }

    /**
     * Returns the current contents of the user's active cart.
     */
    public Result<OrderDTO> viewCart(String token) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to view cart");
            return Result.failure("Unauthorized: invalid token");
        }
        String userId = authGateway.extractUserId(token);

        Optional<ActiveOrder> orderOpt = orderRepository.findActiveByUserId(userId);
        if (orderOpt.isEmpty()) return Result.failure("No active cart found");

        ActiveOrder order = orderOpt.get();
        List<OrderItemDTO> itemDTOs = order.getItems().stream()
                .map(i -> new OrderItemDTO(i.getEventId(), i.getZoneId(), i.getSeatId(), i.getBasePrice()))
                .collect(Collectors.toList());

        return Result.success(new OrderDTO(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getExpiresAt(),
                order.getItems().stream().mapToDouble(OrderItem::getBasePrice).sum(),
                itemDTOs));
    }

    /**
     * Removes a single item from the user's cart and releases its seat hold.
     */
    public Result<Void> removeItemFromCart(String token, String eventId, String zoneId, String seatId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to remove item from cart");
            return Result.failure("Unauthorized: invalid token");
        }
        String userId = authGateway.extractUserId(token);

        Optional<ActiveOrder> orderOpt = orderRepository.findActiveByUserId(userId);
        if (orderOpt.isEmpty()) return Result.failure("No active cart found");

        ActiveOrder order = orderOpt.get();
        boolean removed = order.getItems().removeIf(i ->
                i.getEventId().equals(eventId) && i.getZoneId().equals(zoneId) && i.getSeatId().equals(seatId));

        if (!removed) return Result.failure("Item not found in cart");

        releaseHold(userId, eventId, zoneId, seatId);
        orderRepository.save(order);
        return Result.success();
    }

    /**
     * Cancels the user's entire cart and releases all held seats.
     */
    public Result<Void> cancelCart(String token) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to cancel cart");
            return Result.failure("Unauthorized: invalid token");
        }
        String userId = authGateway.extractUserId(token);

        Optional<ActiveOrder> orderOpt = orderRepository.findActiveByUserId(userId);
        if (orderOpt.isEmpty()) return Result.success();

        ActiveOrder order = orderOpt.get();
        for (OrderItem item : order.getItems()) {
            releaseHold(userId, item.getEventId(), item.getZoneId(), item.getSeatId());
        }

        orderRepository.deleteById(order.getId());
        logger.info("Cart {} cancelled for user {}", order.getId(), userId);
        return Result.success();
    }

    // ── Private helpers ───────────────────────────────────────────────────────────

    private void releaseHold(String userId, String eventId, String zoneId, String seatId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            try {
                Zone zone = event.getZoneById(zoneId);
                if (zone instanceof SeatedZone sz) {
                    sz.findSeatById(seatId).ifPresent(s -> s.release());
                } else if (zone instanceof StandingZone stz) {
                    stz.releaseStandingSpot(userId);
                }
                eventRepository.save(event);
            } catch (Exception e) {
                logger.warn("Failed to release hold for user {} seat {} zone {}: {}", userId, seatId, zoneId, e.getMessage());
            }
        });
    }
}