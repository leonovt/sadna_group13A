package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import java.util.List;

public class FakeRaffleJpaRepository extends AbstractFakeJpaRepository<RaffleEntity, String> implements RaffleJpaRepository {

    public FakeRaffleJpaRepository() {
        super(RaffleEntity::getId);
    }

    @Override
    public List<RaffleEntity> findByEventId(String eventId) {
        return findAll().stream().filter(e -> e.getEventId().equals(eventId)).toList();
    }

    @Override
    public List<RaffleEntity> findByCompanyId(String companyId) {
        return findAll().stream().filter(e -> e.getCompanyId().equals(companyId)).toList();
    }
}
