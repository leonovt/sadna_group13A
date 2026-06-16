package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import java.util.List;

public class FakeEventJpaRepository extends AbstractFakeJpaRepository<EventEntity, String> implements EventJpaRepository {

    public FakeEventJpaRepository() {
        super(EventEntity::getId);
    }

    @Override
    public List<EventEntity> findByCompanyId(String companyId) {
        return findAll().stream().filter(e -> e.getCompanyId().equals(companyId)).toList();
    }

    @Override
    public List<EventEntity> findByPublishedTrue() {
        return findAll().stream().filter(EventEntity::isPublished).toList();
    }

    @Override
    public List<EventEntity> findByTitleContainingIgnoreCase(String titleFragment) {
        String lower = titleFragment.toLowerCase();
        return findAll().stream().filter(e -> e.getTitle().toLowerCase().contains(lower)).toList();
    }

    @Override
    public List<EventEntity> findByCategory(String category) {
        return findAll().stream().filter(e -> category.equals(e.getCategory())).toList();
    }
}
