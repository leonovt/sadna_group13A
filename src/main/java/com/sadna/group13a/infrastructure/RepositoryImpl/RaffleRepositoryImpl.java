package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.Raffle.Raffle;
import com.sadna.group13a.domain.Interfaces.IRaffleRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class RaffleRepositoryImpl implements IRaffleRepository {

    private final ConcurrentHashMap<String, Raffle> store = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(Raffle raffle) {
        Raffle stored = store.get(raffle.getId());
        if (stored != null && stored != raffle && stored.getVersion() > raffle.getVersion()) {
            throw new OptimisticLockException(
                    "Optimistic lock conflict for Raffle " + raffle.getId() +
                    ": stored version " + stored.getVersion() +
                    " > incoming version " + raffle.getVersion());
        }
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

    @Override
    public List<Raffle> findByUserId(String userId) {
        return store.values().stream()
                .filter(r -> r.getParticipantUserIds().contains(userId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Raffle> findByCompanyId(String companyId) {
        return store.values().stream()
                .filter(r -> r.getCompanyId().equals(companyId))
                .collect(Collectors.toList());
    }
}
