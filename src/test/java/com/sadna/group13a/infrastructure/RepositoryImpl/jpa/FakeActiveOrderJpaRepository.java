package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import java.util.Optional;

public class FakeActiveOrderJpaRepository extends AbstractFakeJpaRepository<ActiveOrderEntity, String> implements ActiveOrderJpaRepository {

    public FakeActiveOrderJpaRepository() {
        super(ActiveOrderEntity::getId);
    }

    @Override
    public Optional<ActiveOrderEntity> findByUserId(String userId) {
        return findAll().stream().filter(e -> e.getUserId().equals(userId)).findFirst();
    }
}
