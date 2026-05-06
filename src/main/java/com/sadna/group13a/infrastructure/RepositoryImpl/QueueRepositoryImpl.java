package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.Interfaces.IQueueRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
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
    public synchronized void save(TicketQueue queue) {
        TicketQueue stored = store.get(queue.getEventId());
        if (stored != null && stored != queue && stored.getVersion() > queue.getVersion()) {
            throw new OptimisticLockException(
                    "Optimistic lock conflict for TicketQueue " + queue.getEventId() +
                    ": stored version " + stored.getVersion() +
                    " > incoming version " + queue.getVersion());
        }
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
