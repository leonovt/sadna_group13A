package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import com.sadna.group13a.domain.Aggregates.User.User;

import java.util.Optional;

public class FakeUserJpaRepository extends AbstractFakeJpaRepository<User, String> implements UserJpaRepository {

    public FakeUserJpaRepository() {
        super(User::getId);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return findAll().stream().filter(e -> e.getUsername().equals(username)).findFirst();
    }

    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }
}
