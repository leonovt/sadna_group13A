package com.sadna.group13a.application.Services;

import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Events.CartExpiredEvent;
import com.sadna.group13a.domain.Interfaces.IActiveOrderRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CartCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(CartCleanupService.class);

    private final IActiveOrderRepository orderRepository;
    private final IEventRepository eventRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CartCleanupService(IActiveOrderRepository orderRepository,
                              IEventRepository eventRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expireStaleOrders() {
        logger.debug("Cart cleanup scheduled task started.");
        try {
            List<ActiveOrder> expired = orderRepository.findAll().stream()
                    .filter(ActiveOrder::isExpired)
                    .toList();

            if (expired.isEmpty()) {
                logger.debug("Cart cleanup: no expired carts found.");
                return;
            }

            int cleaned = 0;
            for (ActiveOrder order : expired) {
                try {
                    for (OrderItem item : order.getItems()) {
                        releaseHold(order.getUserId(), item.getEventId(), item.getZoneId(), item.getSeatId());
                    }
                    orderRepository.deleteById(order.getId());
                    eventPublisher.publishEvent(new CartExpiredEvent(order.getUserId(), order.getId()));
                    logger.info("Expired cart '{}' for user '{}' deleted ({} item(s)).",
                            order.getId(), order.getUserId(), order.getItems().size());
                    cleaned++;
                } catch (Exception e) {
                    logger.error("Failed to clean up expired cart '{}' for user '{}': {}",
                            order.getId(), order.getUserId(), e.getMessage(), e);
                }
            }

            logger.info("Cart cleanup complete: {} expired cart(s) removed.", cleaned);
        } catch (Exception e) {
            logger.error("Cart cleanup task failed unexpectedly: {}", e.getMessage(), e);
        }
    }

    private void releaseHold(String userId, String eventId, String zoneId, String seatId) {
        var eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("Cannot release hold for user '{}': event '{}' not found (zone={}, seat={}).",
                    userId, eventId, zoneId, seatId);
            return;
        }
        var event = eventOpt.get();
        try {
            event.releaseItem(zoneId, seatId, userId);
            eventRepository.save(event);
            logger.debug("Released hold for user '{}' on event '{}' zone='{}' seat='{}'.",
                    userId, eventId, zoneId, seatId);
        } catch (Exception e) {
            logger.warn("Failed to release hold for expired cart user='{}' event='{}' zone='{}' seat='{}': {}",
                    userId, eventId, zoneId, seatId, e.getMessage());
        }
    }
}
