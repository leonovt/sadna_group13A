package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

public class FakeCompanyJpaRepository extends AbstractFakeJpaRepository<CompanyEntity, String> implements CompanyJpaRepository {

    public FakeCompanyJpaRepository() {
        super(CompanyEntity::getId);
    }
}
