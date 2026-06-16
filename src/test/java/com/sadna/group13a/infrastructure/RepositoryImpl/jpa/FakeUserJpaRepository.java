package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import java.util.Optional;

public class FakeUserJpaRepository extends AbstractFakeJpaRepository<UserEntity, String> implements UserJpaRepository {

    public FakeUserJpaRepository() {
        super(UserEntity::getId);
    }

    @Override
    public Optional<UserEntity> findByUsername(String username) {
        return findAll().stream().filter(e -> e.getUsername().equals(username)).findFirst();
    }

    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }
}
