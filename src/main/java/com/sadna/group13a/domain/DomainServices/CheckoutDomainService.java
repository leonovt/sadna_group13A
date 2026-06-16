package com.sadna.group13a.domain.DomainServices;

import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistoryItem;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.policies.discount.AdditiveDiscountPolicy;
import com.sadna.group13a.domain.policies.purchase.AndPolicy;
import com.sadna.group13a.domain.shared.DiscountContext;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.PurchaseContext;
import com.sadna.group13a.domain.shared.PurchasePolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Domain Service — pure Java, no Spring annotations.
 * Finalizes checkout for a single event's items: validates the purchase policy,
 * transitions seat states from HELD to SOLD, calculates the discounted price,
 * and returns immutable receipt line items.
 *
 * If any seat transition fails mid-way, already-sold seats within this call are
 * automatically rolled back so callers always get all-or-nothing atomicity per event.
 */
public class CheckoutDomainService {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutDomainService.class);

    /**
     * Processes a set of cart items belonging to one event.
     *
     * @param items           the cart items to check out (all must belong to {@code event})
     * @param order           the parent cart — used for expiry check and buyer identity
     * @param event           the event whose seat map will be mutated (HELD → SOLD)
     * @param company         the owning company — used for denormalized receipt data
     * @param purchasePolicy  combined (AND) event + company purchase policy root
     * @param discountPolicy  combined (Additive/Max) event + company discount policy root
     * @param purchaseCtx     buyer context passed to every purchase policy evaluation
     * @param discountCtx     buyer context passed to every discount calculation
     * @return receipt line items for the successfully checked-out seats
     * @throws DomainException           if the order expired or the purchase policy blocks the sale
     * @throws com.sadna.group13a.domain.shared.SeatUnavailableException
     *         if a seat hold has expired or was taken by another user
     */
    public List<OrderHistoryItem> checkoutItemsForEvent(
            List<OrderItem> items,
            ActiveOrder order,
            Event event,
            ProductionCompany company,
            PurchasePolicy purchasePolicy,
            DiscountPolicy discountPolicy,
            PurchaseContext purchaseCtx,
            DiscountContext discountCtx) {

        if (order.isExpired()) {
            logger.warn("Checkout rejected for order '{}' (user '{}'): order has expired.", order.getId(), order.getUserId());
            throw new DomainException("Order has expired and can no longer be checked out");
        }

        if (!purchasePolicy.isSatisfied(purchaseCtx)) {
            logger.warn("Checkout blocked for order '{}' (user '{}', event '{}'): purchase policy not satisfied.",
                    order.getId(), order.getUserId(), event.getId());
            throw new DomainException("Purchase is not permitted by the current purchase policy");
        }

        List<OrderHistoryItem> historyItems  = new ArrayList<>();
        List<Runnable>         rollbackActions = new ArrayList<>();

        try {
            for (OrderItem item : items) {
                String zoneId = item.getZoneId();
                String seatId = item.getSeatId();

                String seatLabel = event.sellItem(zoneId, seatId, order.getUserId());
                rollbackActions.add(() -> event.unsellItem(zoneId, seatId));

                double basePrice    = event.getZoneBasePrice(zoneId);
                double discount     = discountPolicy.calculateDiscount(basePrice, discountCtx);
                double finalPrice   = Math.max(0.0, basePrice - discount);

                historyItems.add(new OrderHistoryItem(
                        item.getEventId(),
                        event.getTitle(),
                        event.getEventDate(),
                        company.getId(),
                        company.getName(),
                        event.getZoneName(zoneId),
                        seatLabel,
                        finalPrice));
            }
        } catch (Exception e) {
            logger.warn("Seat transition failed for order '{}' (user '{}', event '{}'): {} — rolling back {} sold seat(s).",
                    order.getId(), order.getUserId(), event.getId(), e.getMessage(), rollbackActions.size());
            for (Runnable rollback : rollbackActions) {
                try {
                    rollback.run();
                } catch (Exception re) {
                    logger.error("Rollback failed during checkout of order '{}' (event '{}'): {}",
                            order.getId(), event.getId(), re.getMessage());
                }
            }
            throw e;
        }

        logger.debug("Checkout complete for order '{}' (user '{}', event '{}'): {} item(s) sold.",
                order.getId(), order.getUserId(), event.getId(), historyItems.size());
        return historyItems;
    }

    /**
     * Reverts all sold seats for the given events back to their held/available state.
     * Best-effort: logs but swallows individual failures so every seat is attempted.
     * Callers are responsible for persisting the mutated event aggregates afterward.
     *
     * @param processedEvents map of eventId → event aggregate whose seats should be unsold
     * @param items           the order items identifying which seats to roll back
     */
    public void unsellSeats(Map<String, Event> processedEvents, List<OrderItem> items) {
        for (OrderItem item : items) {
            Event event = processedEvents.get(item.getEventId());
            if (event == null) continue;
            try {
                event.unsellItem(item.getZoneId(), item.getSeatId());
            } catch (Exception e) {
                logger.warn("Rollback failed for seat '{}' zone '{}': {}", item.getSeatId(), item.getZoneId(), e.getMessage());
            }
        }
    }

    /**
     * Combines the event-level and company-level purchase policies using AND
     * semantics: both must be satisfied for a purchase to proceed.
     */
    public PurchasePolicy combinePolicies(PurchasePolicy eventPolicy, PurchasePolicy companyPolicy) {
        return new AndPolicy(eventPolicy, companyPolicy);
    }

    /**
     * Combines the event-level and company-level discount policies so that both
     * discounts are summed (additive).
     */
    public DiscountPolicy combineDiscounts(DiscountPolicy eventDiscount, DiscountPolicy companyDiscount) {
        return new AdditiveDiscountPolicy(List.of(eventDiscount, companyDiscount));
    }
}
