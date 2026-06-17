package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.UserJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepositoryImpl implements IUserRepository {

    private final UserJpaRepository jpa;

    public UserRepositoryImpl(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findById(String id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username);
    }

    @Override
    public synchronized void save(User user) {
        Optional<User> storedEntity = jpa.findById(user.getId());

        if (storedEntity.isPresent()) {
            User stored = storedEntity.get();
            if (stored.getVersion() > user.getVersion()) {
                throw new OptimisticLockException(
                        "Optimistic lock conflict for User " + user.getId() +
                        ": stored version " + stored.getVersion() +
                        " >= incoming version " + user.getVersion());
            }
        } else if (jpa.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists: " + user.getUsername());
        }

        jpa.save(user);
    }

    @Override
    public void delete(String id) {
        jpa.deleteById(id);
    }

    @Override
    public List<User> findAll() {
        return jpa.findAll();
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.existsByUsername(username);
    }
}
