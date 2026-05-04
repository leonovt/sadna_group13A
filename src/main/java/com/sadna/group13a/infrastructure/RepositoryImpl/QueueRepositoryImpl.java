package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.Interfaces.IQueueRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class QueueRepositoryImpl implements IQueueRepository {

    private final ConcurrentHashMap<String, TicketQueue> store = new ConcurrentHashMap<>();

    @Override
    public Optional<TicketQueue> findByEventId(String eventId) {
        return Optional.ofNullable(store.get(eventId));
    }

    @Override
    public void save(TicketQueue queue) {
        store.put(queue.getEventId(), queue);
    }

    @Override
    public List<TicketQueue> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void delete(String eventId) {
        store.remove(eventId);
    }
}
