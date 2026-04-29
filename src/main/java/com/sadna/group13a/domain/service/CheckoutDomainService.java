package com.sadna.group13a.domain.service;

import com.sadna.group13a.domain.company.ProductionCompany;
import com.sadna.group13a.domain.event.Event;
import com.sadna.group13a.domain.event.Seat;
import com.sadna.group13a.domain.event.SeatedZone;
import com.sadna.group13a.domain.event.StandingZone;
import com.sadna.group13a.domain.event.Zone;
import com.sadna.group13a.domain.external.IPaymentGateway;
import com.sadna.group13a.domain.external.ITicketSupplier;
import com.sadna.group13a.domain.order.ActiveOrder;
import com.sadna.group13a.domain.order.OrderHistory;
import com.sadna.group13a.domain.order.OrderHistoryItem;
import com.sadna.group13a.domain.order.OrderItem;
import com.sadna.group13a.domain.policy.DiscountPolicy;
import com.sadna.group13a.domain.policy.PurchasePolicy;
import com.sadna.group13a.domain.shared.DomainException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain service that orchestrates the complex checkout process.
 * It coordinates across multiple aggregates (Order, Event, Company, Policies)
 * and interfaces with external providers.
 */
public class CheckoutDomainService {

    private final IPaymentGateway paymentGateway;
    private final ITicketSupplier ticketSupplier;

    public CheckoutDomainService(IPaymentGateway paymentGateway, ITicketSupplier ticketSupplier) {
        if (paymentGateway == null) throw new IllegalArgumentException("paymentGateway cannot be null");
        if (ticketSupplier == null) throw new IllegalArgumentException("ticketSupplier cannot be null");
        
        this.paymentGateway = paymentGateway;
        this.ticketSupplier = ticketSupplier;
    }

    /**
     * Executes the checkout process.
     *
     * @param order          The active order to checkout.
     * @param event          The event for which the tickets are being bought.
     * @param company        The production company that owns the event.
     * @param purchasePolicy The applicable purchase policy (can be null).
     * @param discountPolicy The applicable discount policy (can be null).
     * @param paymentDetails Payment details string (e.g. secure token).
     * @return The finalized immutable OrderHistory.
     */
    public OrderHistory checkout(ActiveOrder order, Event event, ProductionCompany company,
                                 PurchasePolicy purchasePolicy, DiscountPolicy discountPolicy,
                                 String paymentDetails) {

        if (order.isExpired()) {
            throw new DomainException("Cannot checkout an expired order");
        }

        // 1. Verify policy
        if (purchasePolicy != null && !purchasePolicy.isSatisfied()) {
            throw new DomainException("Order does not satisfy the purchase policy");
        }

        // 2. Calculate price
        double basePrice = order.calculateTotalBasePrice();
        double finalPrice = basePrice;
        if (discountPolicy != null) {
            finalPrice -= discountPolicy.calculateDiscount(basePrice);
        }

        // 3. Process payment
        String transactionId = paymentGateway.processPayment(order.getUserId(), finalPrice, paymentDetails);
        if (transactionId == null || transactionId.isBlank()) {
            throw new DomainException("Payment failed or was rejected by the gateway");
        }

        // 4. Update ActiveOrder status to COMPLETED
        order.complete(transactionId);

        // 5. Generate tickets & construct history items
        List<OrderHistoryItem> historyItems = new ArrayList<>();

        for (OrderItem item : order.getItems()) {
            Zone zone = event.getZoneById(item.getZoneId());
            
            // Transition seats/standing spots from HELD to SOLD
            if (zone instanceof SeatedZone) {
                SeatedZone seatedZone = (SeatedZone) zone;
                Seat seat = seatedZone.findSeatById(item.getSeatId())
                        .orElseThrow(() -> new DomainException("Seat not found: " + item.getSeatId()));
                seat.sell(order.getUserId());
            } else if (zone instanceof StandingZone) {
                StandingZone standingZone = (StandingZone) zone;
                standingZone.sellStandingSpot(order.getUserId());
            } else {
                throw new DomainException("Unknown zone type");
            }

            // Generate external ticket barcode
            String ticketBarcode = ticketSupplier.generateTicket(order.getOrderId(), event.getId(), item.getSeatId());
            // Currently our OrderHistoryItem doesn't store the barcode, but a real system would.
            // We just ensure the supplier was successfully called.

            historyItems.add(new OrderHistoryItem(
                    event.getId(),
                    event.getTitle(),
                    event.getEventDate(),
                    company.getId(),
                    company.getName(),
                    zone.getName(),
                    item.getSeatId(),
                    item.getBasePrice() // Simplified: using base price instead of prorated final price
            ));
        }

        // 6. Return immutable OrderHistory receipt
        return new OrderHistory(
                UUID.randomUUID().toString(),
                order.getUserId(),
                LocalDateTime.now(),
                finalPrice,
                historyItems
        );
    }
}
