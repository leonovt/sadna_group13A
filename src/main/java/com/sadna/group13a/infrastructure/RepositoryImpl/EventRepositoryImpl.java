package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.EventEntity;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.EventJpaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class EventRepositoryImpl implements IEventRepository {

    private final EventJpaRepository jpa;
    private final ObjectMapper objectMapper;

    public EventRepositoryImpl(EventJpaRepository jpa,
                                @Qualifier("domainObjectMapper") ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Event> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public synchronized void save(Event event) {
        Optional<EventEntity> storedEntity = jpa.findById(event.getId());
        if (storedEntity.isPresent()) {
            Event stored = toDomain(storedEntity.get());
            if (stored.getVersion() >= event.getVersion()) {
                throw new OptimisticLockException(
                        "Optimistic lock conflict for Event " + event.getId() +
                        ": stored version " + stored.getVersion() +
                        " > incoming version " + event.getVersion());
            }
        }
        jpa.save(new EventEntity(event.getId(), event.getCompanyId(), event.getCategory(),
                event.isPublished(), event.getTitle(), writeJson(event)));
    }

    @Override
    public void delete(String id) {
        jpa.deleteById(id);
    }

    @Override
    public List<Event> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Event> findByCompanyId(String companyId) {
        return jpa.findByCompanyId(companyId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Event> findPublished() {
        return jpa.findByPublishedTrue().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Event> searchByTitle(String query) {
        return jpa.findByTitleContainingIgnoreCase(query).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Event> findByCategory(String category) {
        return jpa.findByCategory(category).stream().map(this::toDomain).toList();
    }

    private String writeJson(Event event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Event " + event.getId(), e);
        }
    }

    private Event toDomain(EventEntity entity) {
        try {
            return objectMapper.readValue(entity.getData(), Event.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize Event " + entity.getId(), e);
        }
    }
}
