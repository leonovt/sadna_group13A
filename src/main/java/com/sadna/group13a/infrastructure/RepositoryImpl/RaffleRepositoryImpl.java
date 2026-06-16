package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.domain.Aggregates.Raffle.Raffle;
import com.sadna.group13a.domain.Interfaces.IRaffleRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.RaffleEntity;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.RaffleJpaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RaffleRepositoryImpl implements IRaffleRepository {

    private final RaffleJpaRepository jpa;
    private final ObjectMapper objectMapper;

    public RaffleRepositoryImpl(RaffleJpaRepository jpa,
                                 @Qualifier("domainObjectMapper") ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void save(Raffle raffle) {
        Optional<RaffleEntity> storedEntity = jpa.findById(raffle.getId());
        if (storedEntity.isPresent()) {
            Raffle stored = toDomain(storedEntity.get());
            if (stored.getVersion() > raffle.getVersion()) {
                throw new OptimisticLockException(
                        "Optimistic lock conflict for Raffle " + raffle.getId() +
                        ": stored version " + stored.getVersion() +
                        " > incoming version " + raffle.getVersion());
            }
        }
        jpa.save(new RaffleEntity(raffle.getId(), raffle.getEventId(), raffle.getCompanyId(), writeJson(raffle)));
    }

    @Override
    public Optional<Raffle> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<Raffle> findByEventId(String eventId) {
        return jpa.findByEventId(eventId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Raffle> findByUserId(String userId) {
        return jpa.findAll().stream()
                .map(this::toDomain)
                .filter(r -> r.getParticipantUserIds().contains(userId))
                .toList();
    }

    @Override
    public List<Raffle> findByCompanyId(String companyId) {
        return jpa.findByCompanyId(companyId).stream().map(this::toDomain).toList();
    }

    private String writeJson(Raffle raffle) {
        try {
            return objectMapper.writeValueAsString(raffle);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Raffle " + raffle.getId(), e);
        }
    }

    private Raffle toDomain(RaffleEntity entity) {
        try {
            return objectMapper.readValue(entity.getData(), Raffle.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize Raffle " + entity.getId(), e);
        }
    }
}
