package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.UserEntity;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.UserJpaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepositoryImpl implements IUserRepository {

    private final UserJpaRepository jpa;
    private final ObjectMapper objectMapper;

    public UserRepositoryImpl(UserJpaRepository jpa,
                               @Qualifier("domainObjectMapper") ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<User> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username).map(this::toDomain);
    }

    @Override
    public synchronized void save(User user) {
        Optional<UserEntity> storedEntity = jpa.findById(user.getId());

        if (storedEntity.isPresent()) {
            User stored = toDomain(storedEntity.get());
            if (stored.getVersion() >= user.getVersion()) {
                throw new OptimisticLockException(
                        "Optimistic lock conflict for User " + user.getId() +
                        ": stored version " + stored.getVersion() +
                        " >= incoming version " + user.getVersion());
            }
        } else if (jpa.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists: " + user.getUsername());
        }

        jpa.save(new UserEntity(user.getId(), user.getUsername(), writeJson(user)));
    }

    @Override
    public void delete(String id) {
        jpa.deleteById(id);
    }

    @Override
    public List<User> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.existsByUsername(username);
    }

    private String writeJson(User user) {
        try {
            return objectMapper.writeValueAsString(user);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize User " + user.getId(), e);
        }
    }

    private User toDomain(UserEntity entity) {
        try {
            return objectMapper.readValue(entity.getData(), User.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize User " + entity.getId(), e);
        }
    }
}
