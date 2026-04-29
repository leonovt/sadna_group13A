package com.sadna.group13a.application.service;

import com.sadna.group13a.application.dto.OrderDTO;
import com.sadna.group13a.application.dto.OrderHistoryDTO;
import com.sadna.group13a.application.dto.OrderHistoryItemDTO;
import com.sadna.group13a.application.dto.Result;
import com.sadna.group13a.domain.company.ICompanyRepository;
import com.sadna.group13a.domain.company.ProductionCompany;
import com.sadna.group13a.domain.event.Event;
import com.sadna.group13a.domain.event.IEventRepository;
import com.sadna.group13a.domain.order.ActiveOrder;
import com.sadna.group13a.domain.order.IHistoryRepository;
import com.sadna.group13a.domain.order.IOrderRepository;
import com.sadna.group13a.domain.order.OrderHistory;
import com.sadna.group13a.domain.order.OrderItem;
import com.sadna.group13a.domain.service.CheckoutDomainService;
import com.sadna.group13a.domain.shared.UserType;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for managing shopping carts and checkout.
 * Implements UC 1.6 (Reserve Seats), UC 1.8 (Checkout Cart).
 */
public class OrderService {
    private final IOrderRepository orderRepository;
    private final IHistoryRepository historyRepository;
    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final CheckoutDomainService checkoutDomainService;

    public OrderService(IOrderRepository orderRepository, IHistoryRepository historyRepository,
                        IEventRepository eventRepository, ICompanyRepository companyRepository,
                        CheckoutDomainService checkoutDomainService) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.checkoutDomainService = checkoutDomainService;
    }

    /**
     * Adds an item to the user's active order. If no order exists, creates one.
     */
    public Result<String> addItemToCart(String userId, UserType userType, String eventId, String zoneId, String seatId) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found");
        
        Event event = eventOpt.get();
        if (!event.isPublished()) return Result.failure("Event is not published");

        // Try to hold the seat.
        try {
            com.sadna.group13a.domain.event.Zone zone = event.getZoneById(zoneId);
            if (zone instanceof com.sadna.group13a.domain.event.SeatedZone sz) {
                sz.findSeatById(seatId).orElseThrow(() -> new Exception("Seat not found")).hold(userId);
            } else if (zone instanceof com.sadna.group13a.domain.event.StandingZone stz) {
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
    public Result<OrderHistoryDTO> checkoutCart(String userId, String paymentDetails) {
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
        
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
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

    public Result<OrderDTO> viewCart(String userId) {
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

    public Result<Void> removeItemFromCart(String userId, String eventId, String zoneId, String seatId) {
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
                com.sadna.group13a.domain.event.Zone zone = event.getZoneById(zoneId);
                if (zone instanceof com.sadna.group13a.domain.event.SeatedZone sz) {
                    sz.findSeatById(seatId).ifPresent(s -> s.release());
                } else if (zone instanceof com.sadna.group13a.domain.event.StandingZone stz) {
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

    public Result<Void> cancelCart(String userId) {
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
