package com.sadna.group13a.domain.Interfaces;

import java.util.List;
import java.util.Optional;

import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;

public interface IQueueRepository {
    Optional<TicketQueue> findByEventId(String eventId);
    void save(TicketQueue queue);
    List<TicketQueue> findAll();
    void delete(String eventId);
}
