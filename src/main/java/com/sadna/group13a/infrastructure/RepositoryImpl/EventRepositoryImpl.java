package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class EventRepositoryImpl implements IEventRepository {

    private final ConcurrentHashMap<String, Event> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Event> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * Persists the event.  Detects optimistic-lock conflicts when the caller holds a
     * different object instance than what is currently in the store (e.g., after
     * deserialization or a future JPA migration): if the stored version is newer than
     * the incoming version, the update is rejected.
     *
     * In the current in-memory implementation the store usually holds the same object
     * reference, so version divergence only occurs with explicit concurrency scenarios.
     */
    @Override
    public synchronized void save(Event event) {
        Event stored = store.get(event.getId());
        if (stored != null && stored != event && stored.getVersion() > event.getVersion()) {
            throw new OptimisticLockException(
                    "Optimistic lock conflict for Event " + event.getId() +
                    ": stored version " + stored.getVersion() +
                    " > incoming version " + event.getVersion());
        }
        store.put(event.getId(), event);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }

    @Override
    public List<Event> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Event> findByCompanyId(String companyId) {
        return store.values().stream()
                .filter(e -> e.getCompanyId().equals(companyId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Event> findPublished() {
        return store.values().stream()
                .filter(Event::isPublished)
                .collect(Collectors.toList());
    }

    @Override
    public List<Event> searchByTitle(String query) {
        String lower = query.toLowerCase();
        return store.values().stream()
                .filter(e -> e.getTitle().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    @Override
    public List<Event> findByCategory(String category) {
        return store.values().stream()
                .filter(e -> category.equals(e.getCategory()))
                .collect(Collectors.toList());
    }
}
