package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class UserRepositoryImpl implements IUserRepository {

    private final ConcurrentHashMap<String, User> store = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return store.values().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public synchronized void save(User user) {
        User stored = store.get(user.getId());

        // 1. Optimistic Locking Check (For updating an existing user)
        if (stored != null && stored != user) {
            // If the incoming version is NOT strictly greater than the stored version, 
            // it means a concurrent update happened and we have a collision.
            if (user.getVersion() <= stored.getVersion()) {
                throw new OptimisticLockException(
                        "Optimistic lock conflict for User " + user.getId() +
                        ": stored version " + stored.getVersion() +
                        " >= incoming version " + user.getVersion());
            }
        }

        // 2. Unique Username Check (For creating a new user)
        // If 'stored' is null, this is a brand new user being registered.
        if (stored == null) {
            boolean usernameTaken = store.values().stream()
                    .anyMatch(u -> u.getUsername().equals(user.getUsername()));
            if (usernameTaken) {
                // Throwing RuntimeException here. If your UserService expects a 
                // specific exception (like DuplicateKeyException), change it here!
                throw new RuntimeException("Username already exists: " + user.getUsername());
            }
        }

        // 3. Save the user
        store.put(user.getId(), user);
    }

    @Override
    public void delete(String id) {
        // Safe to leave unsynchronized. ConcurrentHashMap handles single removals safely.
        store.remove(id);
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public boolean existsByUsername(String username) {
        return store.values().stream().anyMatch(u -> u.getUsername().equals(username));
    }
}