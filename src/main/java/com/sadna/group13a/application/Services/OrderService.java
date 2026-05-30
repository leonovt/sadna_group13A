package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.DTO.OrderDTO;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.OrderHistoryItemDTO;
import com.sadna.group13a.application.DTO.OrderItemDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Aggregates.Company.CompanyStatus;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistoryItem;
import com.sadna.group13a.domain.Aggregates.Raffle.AuthorizationCode;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.DomainServices.CheckoutDomainService;
import com.sadna.group13a.domain.DomainServices.TicketingAccessDomainService;
import com.sadna.group13a.domain.Aggregates.Company.CompanyStaffMember;
import com.sadna.group13a.domain.Events.CheckoutFailedEvent;
import com.sadna.group13a.domain.Events.EventSoldOutEvent;
import com.sadna.group13a.domain.Events.OrderCompletedEvent;
import com.sadna.group13a.domain.Interfaces.IActiveOrderRepository;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IQueueRepository;
import com.sadna.group13a.domain.Interfaces.IRaffleRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.domain.shared.DiscountContext;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.PermissionDeniedException;
import com.sadna.group13a.domain.shared.PurchaseContext;
import com.sadna.group13a.domain.shared.PurchasePolicy;
import com.sadna.group13a.domain.shared.SeatUnavailableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private final ITicketSupplier ticketSupplier;
    private final IAuth authGateway;
    private final CheckoutDomainService checkoutDomainService;
    private final TicketingAccessDomainService ticketingAccessDomainService;
    private final ApplicationEventPublisher eventPublisher;

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    public OrderService(
            IActiveOrderRepository orderRepository,
            IOrderHistoryRepository historyRepository,
            IEventRepository eventRepository,
            ICompanyRepository companyRepository,
            IQueueRepository queueRepository,
            IRaffleRepository raffleRepository,
            IPaymentGateway paymentGateway,
            ITicketSupplier ticketSupplier,
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
        this.ticketSupplier = ticketSupplier;
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
        if (seatId != null) {
            return addBatchItemsToCart(token, eventId, zoneId, List.of(seatId), null);
        } else {
            return addBatchItemsToCart(token, eventId, zoneId, null, 1);
        }
    }

    /**
     * Adds multiple tickets for one event to the user's active cart atomically.
     * For seated zones, provide explicit seat IDs via {@code seatIds}.
     * For standing zones, pass {@code seatIds} as null/empty and set {@code quantity}.
     * If any reservation fails, all already-held seats are released and the cart is unchanged.
     *
     * Thread safety: each seat hold/release delegates to synchronized methods on Seat /
     * StandingZone. The {@code reservedSeats} rollback list is a local variable and is
     * never shared between threads.
     */
    public Result<String> addBatchItemsToCart(String token, String eventId, String zoneId,
                                               List<String> seatIds, Integer quantity) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized addBatchItemsToCart attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized: invalid token");
        }
        String userId = authGateway.extractUserId(token);

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || !userOpt.get().canPurchase()) {
            logger.warn("User '{}' cannot purchase tickets — not an active member.", userId);
            return Result.failure("Only active members can purchase tickets.");
        }

        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("User '{}' tried to add items for non-existent event '{}'.", userId, eventId);
            return Result.failure("Event not found");
        }

        Event event = eventOpt.get();
        if (!event.isPublished()) {
            logger.warn("User '{}' tried to add items for unpublished event '{}'.", userId, eventId);
            return Result.failure("Event is not published");
        }

        Optional<ProductionCompany> companyOpt = companyRepository.findById(event.getCompanyId());
        if (companyOpt.isEmpty() || companyOpt.get().getStatus() != CompanyStatus.ACTIVE) {
            logger.warn("User '{}' tried to add items for event '{}' but company '{}' is not active.",
                    userId, eventId, event.getCompanyId());
            return Result.failure("Company is not active");
        }

        List<String> seatsToReserve;
        if (seatIds != null && !seatIds.isEmpty()) {
            seatsToReserve = new ArrayList<>(seatIds);
        } else {
            if (quantity == null || quantity <= 0) {
                logger.warn("User '{}' supplied invalid quantity '{}' for standing zone '{}' in event '{}'.",
                        userId, quantity, zoneId, eventId);
                return Result.failure("Quantity must be positive for standing zones");
            }
            seatsToReserve = new ArrayList<>(Collections.nCopies(quantity, null));
        }

        List<String> reservedSeats = new ArrayList<>();
        try {
            for (String seatId : seatsToReserve) {
                event.reserveSeat(zoneId, seatId, userId);
                reservedSeats.add(seatId);
            }
            eventRepository.save(event);
        } catch (Exception e) {
            for (String reservedSeatId : reservedSeats) {
                try {
                    event.releaseItem(zoneId, reservedSeatId, userId);
                } catch (Exception re) {
                    logger.warn("Failed to release seat {} during batch rollback: {}",
                            reservedSeatId, re.getMessage());
                }
            }
            logger.warn("Batch reservation failed for user {} in zone {}: {}",
                    userId, zoneId, e.getMessage());
            return Result.failure("Failed to reserve seats: " + e.getMessage());
        }

        double price = event.getZoneBasePrice(zoneId);
        ActiveOrder order = orderRepository.getOrCreate(userId,
                () -> new ActiveOrder(UUID.randomUUID().toString(), userId));

        for (String seatId : seatsToReserve) {
            order.addItem(new OrderItem(eventId, zoneId, seatId, price));
        }
        orderRepository.save(order);

        logger.info("User {} added {} item(s) to cart {} for event {}",
                userId, seatsToReserve.size(), order.getId(), eventId);
        return Result.success(order.getId());
    }

    /**
     * Full checkout flow for a cart that may span multiple events:
     * <ol>
     *   <li>Validates access rights per event (queue / raffle / regular)</li>
     *   <li>Executes domain checkout per event inside a per-event lock</li>
     *   <li>If any event fails, rolls back all already-sold seats and returns failure</li>
     *   <li>Charges payment once for the combined total</li>
     *   <li>If payment fails, rolls back all sold seats and returns failure</li>
     *   <li>Persists seat changes; on persistence error, refunds and rolls back</li>
     * </ol>
     *
     * Policies (purchase + discount) are sourced from BOTH the event and its company
     * and applied during the per-event domain checkout.
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

        Optional<User> checkoutUserOpt = userRepository.findById(userId);
        if (checkoutUserOpt.isEmpty() || !checkoutUserOpt.get().canPurchase()) {
            logger.warn("User '{}' cannot checkout — not an active member.", userId);
            return Result.failure("Only active members can purchase tickets.");
        }

        // ── Fetch and validate the cart ───────────────────────────────────────────
        Optional<ActiveOrder> orderOpt = orderRepository.findById(activeOrderId);
        if (orderOpt.isEmpty()) {
            logger.warn("User '{}' tried to checkout non-existent cart '{}'.", userId, activeOrderId);
            return Result.failure("Cart not found");
        }
        ActiveOrder order = orderOpt.get();

        if (!order.getUserId().equals(userId)) {
            logger.warn("User '{}' attempted to check out cart '{}' belonging to '{}'.", userId, activeOrderId, order.getUserId());
            return Result.failure("Unauthorized: this cart does not belong to you");
        }
        if (order.getItems().isEmpty()) {
            logger.warn("User '{}' tried to checkout empty cart '{}'.", userId, activeOrderId);
            return Result.failure("Cart is empty");
        }
        if (order.isExpired()) {
            logger.warn("User '{}' tried to checkout expired cart '{}'.", userId, activeOrderId);
            return Result.failure("Cart has expired");
        }

        // ── Group items by event ──────────────────────────────────────────────────
        Map<String, List<OrderItem>> itemsByEvent = order.getItems().stream()
                .collect(Collectors.groupingBy(OrderItem::getEventId));

        // Track processed events for rollback
        Map<String, Event> processedEvents = new LinkedHashMap<>();
        Map<String, TicketQueue> processedQueues = new LinkedHashMap<>();
        List<OrderHistoryItem> allHistoryItems = new ArrayList<>();
        double totalPaid = 0.0;

        // ── Per-event access check + domain checkout ──────────────────────────────
        for (Map.Entry<String, List<OrderItem>> entry : itemsByEvent.entrySet()) {
            String eventId = entry.getKey();
            List<OrderItem> eventItems = entry.getValue();

            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (eventOpt.isEmpty()) {
                logger.warn("Checkout for user '{}': event '{}' not found — rolling back.", userId, eventId);
                rollbackSoldSeats(processedEvents, order.getItems());
                return Result.failure("Event not found: " + eventId);
            }
            Event event = eventOpt.get();

            Optional<ProductionCompany> companyOpt = companyRepository.findById(event.getCompanyId());
            if (companyOpt.isEmpty()) {
                logger.warn("Checkout for user '{}': company not found for event '{}' — rolling back.", userId, eventId);
                rollbackSoldSeats(processedEvents, order.getItems());
                return Result.failure("Company not found for event: " + eventId);
            }
            ProductionCompany company = companyOpt.get();

            // Resolve access-check inputs based on sale mode
            TicketQueue queue = null;
            AuthorizationCode authCode = null;
            if (event.getSaleMode() == EventSaleMode.QUEUE) {
                queue = queueRepository.findByEventId(eventId).orElse(null);
            } else if (event.getSaleMode() == EventSaleMode.RAFFLE) {
                final String eid = eventId;
                authCode = raffleRepository.findByEventId(eid).stream()
                        .findFirst()
                        .flatMap(r -> r.getAuthorizationCodeFor(userId))
                        .filter(c -> c.isValidFor(userId, eid))
                        .orElse(null);
            }

            try {
                ticketingAccessDomainService.validateEventIsOpenForSale(event);
                ticketingAccessDomainService.validateAccess(event, userId, queue, authCode);
            } catch (PermissionDeniedException e) {
                logger.warn("Access denied for user {} on event {}: {}", userId, eventId, e.getMessage());
                rollbackSoldSeats(processedEvents, order.getItems());
                eventPublisher.publishEvent(new CheckoutFailedEvent(userId, e.getMessage()));
                return Result.failure(e.getMessage());
            }

            // Combine purchase policies: both event AND company rules must pass
            PurchasePolicy combinedPurchase = checkoutDomainService.combinePolicies(
                    event.getPurchasePolicy(), company.getPurchasePolicy());

            // Combine discount policies: sum discounts from event and company (additive by default)
            DiscountPolicy combinedDiscount = checkoutDomainService.combineDiscounts(
                    event.getDiscountPolicy(), company.getDiscountPolicy());

            // Build checkout contexts — userAge defaults to 0 until user age storage is added.
            // optionalAuthCode doubles as coupon code for non-raffle events.
            int ticketCount = eventItems.size();
            int userAge = (checkoutUserOpt.get() instanceof Member m) ? m.getAge() : 0;
            PurchaseContext purchaseCtx = new PurchaseContext(userId, ticketCount, userAge, optionalAuthCode);
            DiscountContext  discountCtx = new DiscountContext(userId, ticketCount, optionalAuthCode);

            // Seat-level synchronization (on Seat and StandingZone methods) ensures
            // that two users competing for the same seat get correct all-or-nothing
            // behaviour without blocking unrelated seats on the same event.
            try {
                List<OrderHistoryItem> items = checkoutDomainService.checkoutItemsForEvent(
                        eventItems, order, event, company,
                        combinedPurchase, combinedDiscount, purchaseCtx, discountCtx);
                allHistoryItems.addAll(items);
                totalPaid += items.stream().mapToDouble(OrderHistoryItem::getPricePaid).sum();
                processedEvents.put(eventId, event);
                if (queue != null) processedQueues.put(eventId, queue);
            } catch (SeatUnavailableException e) {
                logger.warn("Seat unavailable during checkout for user {}: {}", userId, e.getMessage());
                rollbackSoldSeats(processedEvents, order.getItems());
                eventPublisher.publishEvent(new CheckoutFailedEvent(userId, "Seat no longer available: " + e.getMessage()));
                return Result.failure("Seat no longer available: " + e.getMessage());
            } catch (Exception e) {
                logger.error("Checkout domain logic failed for user {}: {}", userId, e.getMessage());
                rollbackSoldSeats(processedEvents, order.getItems());
                eventPublisher.publishEvent(new CheckoutFailedEvent(userId, "Checkout failed: " + e.getMessage()));
                return Result.failure("Checkout failed: " + e.getMessage());
            }
        }

        // ── Payment ───────────────────────────────────────────────────────────────
        Result<String> paymentResult = paymentGateway.processPayment(totalPaid, paymentDetails);
        if (!paymentResult.isSuccess()) {
            logger.warn("Payment declined for user {} (amount {}): {}", userId, totalPaid, paymentResult.getErrorMessage());
            rollbackSoldSeats(processedEvents, order.getItems());
            eventPublisher.publishEvent(new CheckoutFailedEvent(userId, "Payment declined: " + paymentResult.getErrorMessage()));
            return Result.failure("Payment declined: " + paymentResult.getErrorMessage());
        }
        String transactionId = paymentResult.getOrThrow();

        // ── Persist seat changes ──────────────────────────────────────────────────
        // OptimisticLockException here means a concurrent modification raced past the
        // per-event lock (shouldn't happen normally, but handled for safety).
        try {
            for (Event event : processedEvents.values()) {
                eventRepository.save(event);
            }
        } catch (Exception e) {
            logger.error("Failed to persist seat map after payment: {}", e.getMessage());
            paymentGateway.refundPayment(transactionId);
            rollbackSoldSeats(processedEvents, order.getItems());
            orderRepository.deleteById(activeOrderId);
            eventPublisher.publishEvent(new CheckoutFailedEvent(userId, "Concurrent modification detected — payment refunded. Please retry."));
            return Result.failure("Concurrent modification detected — payment refunded. Please retry.");
        }

        // ── Sold-out alerts ───────────────────────────────────────────────────────
        for (Event event : processedEvents.values()) {
            if (event.getTotalAvailable() == 0) {
                companyRepository.findById(event.getCompanyId()).ifPresent(company -> {
                    List<String> staffIds = company.getStaff().values().stream()
                            .map(CompanyStaffMember::getUserId)
                            .collect(Collectors.toList());
                    eventPublisher.publishEvent(
                            new EventSoldOutEvent(event.getId(), event.getTitle(), staffIds));
                });
            }
        }

        // ── Post-checkout ─────────────────────────────────────────────────────────
        OrderHistory history = new OrderHistory(
                UUID.randomUUID().toString(), userId, LocalDateTime.now(), totalPaid, transactionId, allHistoryItems);

        Result<List<String>> ticketResult = ticketSupplier.issueTickets(history.getReceiptId(), allHistoryItems.size());
        if (!ticketResult.isSuccess()) {
            logger.error("Ticket issuance failed for receipt {}: {}", history.getReceiptId(), ticketResult.getErrorMessage());
            paymentGateway.refundPayment(transactionId);
            rollbackSoldSeats(processedEvents, order.getItems());
            orderRepository.deleteById(activeOrderId);
            eventPublisher.publishEvent(new CheckoutFailedEvent(userId, "Ticket issuance failed — payment refunded."));
            return Result.failure("Ticket issuance failed — payment refunded.");
        }

        historyRepository.save(history);
        orderRepository.deleteById(activeOrderId);

        for (Map.Entry<String, TicketQueue> qe : processedQueues.entrySet()) {
            qe.getValue().removeActiveUser(userId);
            queueRepository.save(qe.getValue());
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
        if (orderOpt.isEmpty()) {
            logger.warn("viewCart: no active cart found for user '{}'.", userId);
            return Result.failure("No active cart found");
        }

        ActiveOrder order = orderOpt.get();
        List<OrderItemDTO> itemDTOs = order.getItems().stream()
                .map(i -> new OrderItemDTO(i.getEventId(), i.getZoneId(), i.getSeatId(), i.getBasePrice()))
                .collect(Collectors.toList());

        logger.debug("viewCart: user '{}' retrieved cart '{}' ({} item(s)).", userId, order.getId(), itemDTOs.size());
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
        if (orderOpt.isEmpty()) {
            logger.warn("removeItemFromCart: no active cart found for user '{}'.", userId);
            return Result.failure("No active cart found");
        }

        ActiveOrder order = orderOpt.get();
        boolean removed = order.removeItemByKey(eventId, zoneId, seatId);
        if (!removed) {
            logger.warn("removeItemFromCart: item (event='{}' zone='{}' seat='{}') not found in cart '{}' for user '{}'.",
                    eventId, zoneId, seatId, order.getId(), userId);
            return Result.failure("Item not found in cart");
        }

        releaseHold(userId, eventId, zoneId, seatId);
        orderRepository.save(order);
        logger.info("User '{}' removed item (event='{}' zone='{}' seat='{}') from cart '{}'.",
                userId, eventId, zoneId, seatId, order.getId());
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
        if (orderOpt.isEmpty()) {
            logger.debug("cancelCart: no active cart to cancel for user '{}'.", userId);
            return Result.success();
        }

        ActiveOrder order = orderOpt.get();
        for (OrderItem item : order.getItems()) {
            releaseHold(userId, item.getEventId(), item.getZoneId(), item.getSeatId());
        }

        orderRepository.deleteById(order.getId());
        logger.info("Cart {} cancelled for user {}", order.getId(), userId);
        return Result.success();
    }

    // ── Private helpers ───────────────────────────────────────────────────────────

    /**
     * Rolls back all sold seats for the given processed events.
     * Best-effort: logs but swallows individual rollback errors so all seats are attempted.
     */
    private void rollbackSoldSeats(Map<String, Event> processedEvents, List<OrderItem> items) {
        for (OrderItem item : items) {
            Event event = processedEvents.get(item.getEventId());
            if (event == null) continue;
            try {
                event.unsellItem(item.getZoneId(), item.getSeatId());
            } catch (Exception e) {
                logger.warn("Rollback failed for seat {} zone {}: {}", item.getSeatId(), item.getZoneId(), e.getMessage());
            }
        }
        for (Event event : processedEvents.values()) {
            try {
                eventRepository.save(event);
            } catch (Exception e) {
                logger.warn("Rollback save failed for event {}: {}", event.getId(), e.getMessage());
            }
        }
    }

    private void releaseHold(String userId, String eventId, String zoneId, String seatId) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("releaseHold: event '{}' not found — cannot release hold for user '{}' zone='{}' seat='{}'.",
                    eventId, userId, zoneId, seatId);
            return;
        }
        Event event = eventOpt.get();
        try {
            event.releaseItem(zoneId, seatId, userId);
            eventRepository.save(event);
            logger.debug("releaseHold: released hold for user '{}' on event '{}' zone='{}' seat='{}'.",
                    userId, eventId, zoneId, seatId);
        } catch (Exception e) {
            logger.warn("releaseHold: failed for user '{}' event='{}' zone='{}' seat='{}': {}",
                    userId, eventId, zoneId, seatId, e.getMessage());
        }
    }
}
