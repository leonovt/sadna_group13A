package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.Raffle.Raffle;
import com.sadna.group13a.domain.Interfaces.IRaffleRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class RaffleRepositoryImpl implements IRaffleRepository {

    private final ConcurrentHashMap<String, Raffle> store = new ConcurrentHashMap<>();

    @Override
    public void save(Raffle raffle) {
        store.put(raffle.getId(), raffle);
    }

    @Override
    public Optional<Raffle> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Raffle> findByEventId(String eventId) {
        return store.values().stream()
                .filter(r -> r.getEventId().equals(eventId))
                .collect(Collectors.toList());
    }
}
