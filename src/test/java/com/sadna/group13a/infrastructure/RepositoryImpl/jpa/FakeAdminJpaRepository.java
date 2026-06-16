package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import java.util.Optional;

public class FakeAdminJpaRepository extends AbstractFakeJpaRepository<AdminEntity, String> implements AdminJpaRepository {

    public FakeAdminJpaRepository() {
        super(AdminEntity::getId);
    }

    @Override
    public Optional<AdminEntity> findByUserId(String userId) {
        return findAll().stream().filter(e -> e.getUserId().equals(userId)).findFirst();
    }
}
