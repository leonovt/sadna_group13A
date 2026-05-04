package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
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

    @Override
    public void save(Event event) {
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
