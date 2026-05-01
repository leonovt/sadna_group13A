package com.sadna.group13a.application.Services;


import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.Company;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IActiveOrderRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.StandingZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.Event.Zone;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.User.UserType;
import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.DTO.OrderDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;

import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application service for managing shopping carts and checkout.
 * Implements UC 1.6 (Reserve Seats), UC 1.8 (Checkout Cart).
 */
public class OrderService
{
    private final IUserRepository userRepository;
    private final IActiveOrderRepository orderRepository;
    private final IOrderHistoryRepository historyRepository;
    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final CheckoutDomainService checkoutDomainService;
    private IAuth authGateway;
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    public OrderService(IActiveOrderRepository orderRepository, IOrderHistoryRepository historyRepository,
                        IEventRepository eventRepository, ICompanyRepository companyRepository,
                        CheckoutDomainService checkoutDomainService, IUserRepository userRepository, IAuth authGateway) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.checkoutDomainService = checkoutDomainService;
        this.userRepository = userRepository;
        this.authGateway = authGateway;
    }

    /**
     * Adds an item to the user's active order. If no order exists, creates one.
     */
    public Result<String> addItemToCart(String tokenString, UserType userType, String eventId, String zoneId, String seatId) {
        if(!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized attempt to add item to cart with token: {}", tokenString);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String userId = authGateway.extractUserId(tokenString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found");
        
        Event event = eventOpt.get();
        if (!event.isPublished()) return Result.failure("Event is not published");

        // Try to hold the seat.
        try {
            Zone zone = event.getZoneById(zoneId);
            if (zone instanceof SeatedZone sz) {
                sz.findSeatById(seatId).orElseThrow(() -> new Exception("Seat not found")).hold(userId);
            } else if (zone instanceof StandingZone stz) {
                stz.holdStandingSpot(userId);
            }
            eventRepository.save(event); // persist hold state
        } catch (Exception e) {
            return Result.failure("Failed to reserve seat: " + e.getMessage());
        }

        ActiveOrder order = orderRepository.findActiveByUserId(userId)
            .orElseGet(() -> new ActiveOrder(UUID.randomUUID().toString(), userId, userType));

        // Let's assume price is just the base price of the zone for V1
        double price = event.getZoneById(zoneId).getBasePrice();
        order.addItem(new OrderItem(eventId, zoneId, seatId, price));
        
        orderRepository.save(order);
        return Result.success(order.getOrderId());
    }

    /**
     * Completes the checkout process for the user's cart.
     */
    public Result<OrderHistoryDTO> checkoutCart(String tokenString, String paymentDetails) {
        if(!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized attempt to checkout cart with token: {}", tokenString);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String userId = authGateway.extractUserId(tokenString);
        Optional<ActiveOrder> orderOpt = orderRepository.findActiveByUserId(userId);
        if (orderOpt.isEmpty()) return Result.failure("No active cart found");
        
        ActiveOrder order = orderOpt.get();
        if (order.getItems().isEmpty()) return Result.failure("Cart is empty");

        // Simplified for V1: we assume single event per order, or we take the first item's event
        // In reality, checkout might span multiple events, requiring a loop or complex orchestration.
        String primaryEventId = order.getItems().get(0).getEventId();
        Optional<Event> eventOpt = eventRepository.findById(primaryEventId);
        if (eventOpt.isEmpty()) return Result.failure("Event in cart not found");
        Event event = eventOpt.get();
        
        Optional<Company> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty()) return Result.failure("Company not found");

        try {
            OrderHistory history = checkoutDomainService.checkout(
                order, event, compOpt.get(), null, null, paymentDetails
            );
            
            // Delete ActiveOrder (since it's done) and Save History
            orderRepository.deleteById(order.getOrderId());
            historyRepository.save(history);
            
            // Re-save Event to persist the SOLD statuses
            eventRepository.save(event);

            // Convert to DTO
            var itemDTOs = history.getItems().stream().map(i -> new OrderHistoryItemDTO(
                i.getEventId(), i.getEventTitle(), i.getEventDate(), i.getCompanyName(),
                i.getZoneName(), i.getSeatLabel(), i.getPricePaid()
            )).collect(Collectors.toList());

            OrderHistoryDTO dto = new OrderHistoryDTO(
                history.getReceiptId(), history.getUserId(), history.getPurchaseDate(),
                history.getTotalPaid(), itemDTOs
            );
            
            return Result.success(dto);
        } catch (Exception e) {
            return Result.failure("Checkout failed: " + e.getMessage());
        }
    }

    public Result<OrderDTO> viewCart(String tokenString) {
        if(!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized attempt to view cart with token: {}", tokenString);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String userId = authGateway.extractUserId(tokenString);
        Optional<ActiveOrder> orderOpt = orderRepository.findActiveByUserId(userId);
        if (orderOpt.isEmpty()) return Result.failure("No active cart found");
        
        ActiveOrder order = orderOpt.get();
        var itemDTOs = order.getItems().stream().map(i -> new com.sadna.group13a.application.dto.OrderItemDTO(
            i.getEventId(), i.getZoneId(), i.getSeatId(), i.getBasePrice()
        )).collect(Collectors.toList());
        
        OrderDTO dto = new OrderDTO(
            order.getOrderId(), order.getUserId(), order.getStatus(),
            null, // expiresAt (not fully modeled in V1 dto yet)
            order.getItems().stream().mapToDouble(OrderItem::getBasePrice).sum(),
            itemDTOs
        );
        return Result.success(dto);
    }

    public Result<Void> removeItemFromCart(String tokenString, String eventId, String zoneId, String seatId) {
        if(!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized attempt to remove item from cart with token: {}", tokenString);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String userId = authGateway.extractUserId(tokenString);

        Optional<ActiveOrder> orderOpt = orderRepository.findActiveByUserId(userId);
        if (orderOpt.isEmpty()) return Result.failure("No active cart found");
        
        ActiveOrder order = orderOpt.get();
        boolean removed = order.getItems().removeIf(i -> 
            i.getEventId().equals(eventId) && i.getZoneId().equals(zoneId) && i.getSeatId().equals(seatId));
            
        if (!removed) return Result.failure("Item not found in cart");
        
        // Also we need to un-hold the seat in the Event aggregate
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isPresent()) {
            Event event = eventOpt.get();
            try {
                Zone zone = event.getZoneById(zoneId);
                if (zone instanceof SeatedZone sz) {
                    sz.findSeatById(seatId).ifPresent(s -> s.release());
                } else if (zone instanceof StandingZone stz) {
                    stz.releaseStandingSpot(userId);
                }
                eventRepository.save(event);
            } catch (Exception e) {
                // Ignore if zone/seat not found during unhold
            }
        }
        
        orderRepository.save(order);
        return Result.success();
    }

    public Result<Void> cancelCart(String tokenString) {
        if(!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized attempt to cancel cart with token: {}", tokenString);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String userId = authGateway.extractUserId(tokenString);

        Optional<ActiveOrder> orderOpt = orderRepository.findActiveByUserId(userId);
        if (orderOpt.isEmpty()) return Result.success(); // already empty/canceled
        
        ActiveOrder order = orderOpt.get();
        
        // Release all seats
        for (OrderItem item : order.getItems()) {
            removeItemFromCart(userId, item.getEventId(), item.getZoneId(), item.getSeatId());
        }
        
        orderRepository.deleteById(order.getOrderId());
        return Result.success();
    }
}
