package com.sadna.group13a.domain.DomainServices;

import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.StandingZone;
import com.sadna.group13a.domain.Aggregates.Event.Zone;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistoryItem;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.PurchasePolicy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — pure Java, no Spring annotations.
 * Finalizes a checkout: validates policies, transitions seat states from HELD to SOLD,
 * calculates the discounted total, and produces an immutable OrderHistory.
 */
public class CheckoutDomainService {

    /**
     * Executes the checkout business logic against the provided in-memory aggregates.
     *
     * @param order            the user's active cart (status must be OPEN)
     * @param event            the event whose seat map will be mutated (HELD → SOLD)
     * @param company          the owning company — used for denormalized receipt data
     * @param discountPolicy   optional; null means no discount applied
     * @param purchasePolicy   optional; null means no purchase restrictions
     * @return a fully-populated, immutable OrderHistory aggregate
     * @throws DomainException if the order expired or a purchase policy blocks the sale
     * @throws com.sadna.group13a.domain.shared.SeatUnavailableException if a seat hold expired or was stolen
     */
    public OrderHistory checkout(
            ActiveOrder order,
            Event event,
            ProductionCompany company,
            DiscountPolicy discountPolicy,
            PurchasePolicy purchasePolicy) {

        if (order.isExpired()) {
            throw new DomainException("Order has expired and can no longer be checked out");
        }

        if (purchasePolicy != null && !purchasePolicy.isSatisfied()) {
            throw new DomainException("Purchase is not permitted by the current purchase policy");
        }

        List<OrderHistoryItem> historyItems = new ArrayList<>();
        double totalPaid = 0.0;

        for (OrderItem item : order.getItems()) {
            Zone zone = event.getZoneById(item.getZoneId());

            // Transition seat state HELD → SOLD and build the denormalized receipt line
            String seatLabel = null;
            if (zone instanceof SeatedZone sz) {
                var seat = sz.findSeatById(item.getSeatId())
                        .orElseThrow(() -> new DomainException(
                                "Seat " + item.getSeatId() + " not found in zone " + item.getZoneId()));
                seat.sell(order.getUserId());
                seatLabel = seat.getLabel();
            } else if (zone instanceof StandingZone stz) {
                stz.sellStandingSpot(order.getUserId());
                // seatLabel stays null — standing admission has no assigned seat
            }

            double basePrice = zone.getBasePrice();
            double discount = (discountPolicy != null) ? discountPolicy.calculateDiscount(basePrice) : 0.0;
            double finalPrice = Math.max(0.0, basePrice - discount);
            totalPaid += finalPrice;

            historyItems.add(new OrderHistoryItem(
                    item.getEventId(),
                    event.getTitle(),
                    event.getEventDate(),
                    company.getId(),
                    company.getName(),
                    zone.getName(),
                    seatLabel,
                    finalPrice
            ));
        }

        return new OrderHistory(
                UUID.randomUUID().toString(),
                order.getUserId(),
                LocalDateTime.now(),
                totalPaid,
                historyItems
        );
    }
}
