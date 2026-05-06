package com.sadna.group13a.application.Services;

import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Interfaces.IActiveOrderRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CartCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(CartCleanupService.class);

    private final IActiveOrderRepository orderRepository;
    private final IEventRepository eventRepository;

    public CartCleanupService(IActiveOrderRepository orderRepository, IEventRepository eventRepository) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expireStaleOrders() {
        List<ActiveOrder> expired = orderRepository.findAll().stream()
                .filter(ActiveOrder::isExpired)
                .toList();

        for (ActiveOrder order : expired) {
            for (OrderItem item : order.getItems()) {
                releaseHold(order.getUserId(), item.getEventId(), item.getZoneId(), item.getSeatId());
            }
            orderRepository.deleteById(order.getId());
            logger.info("Expired cart {} for user {} cleaned up.", order.getId(), order.getUserId());
        }
    }

    private void releaseHold(String userId, String eventId, String zoneId, String seatId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            try {
                event.releaseItem(zoneId, seatId, userId);
                eventRepository.save(event);
            } catch (Exception e) {
                logger.warn("Failed to release hold for expired cart user={} zone={} seat={}: {}",
                        userId, zoneId, seatId, e.getMessage());
            }
        });
    }
}
