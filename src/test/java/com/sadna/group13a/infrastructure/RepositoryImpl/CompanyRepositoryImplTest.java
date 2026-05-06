package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


class CompanyRepositoryImplTest {

    private CompanyRepositoryImpl repo;

    @BeforeEach
    void setUp() {
        repo = new CompanyRepositoryImpl();
    }

    @Test
    void givenCompany_whenSave_thenFindByIdReturnsIt() {
        ProductionCompany company = new ProductionCompany("co-1", "Acme", "Events", "founder-1");
        repo.save(company);

        Optional<ProductionCompany> found = repo.findById("co-1");
        assertTrue(found.isPresent());
        assertEquals("Acme", found.get().getName());
    }

    @Test
    void givenNoCompany_whenFindById_thenReturnsEmpty() {
        assertTrue(repo.findById("ghost").isEmpty());
    }

    @Test
    void givenTwoCompanies_whenFindAll_thenReturnsBoth() {
        repo.save(new ProductionCompany("co-1", "Alpha", "Desc", "f-1"));
        repo.save(new ProductionCompany("co-2", "Beta", "Desc", "f-2"));

        List<ProductionCompany> all = repo.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void givenCompanyWithManager_whenFindByManagerId_thenReturnsCompany() {
        ProductionCompany company = new ProductionCompany("co-3", "Corp", "Desc", "founder-1");
        company.nominateStaff("founder-1", "mgr-1", CompanyRole.MANAGER, null);
        company.acceptNomination("mgr-1");
        repo.save(company);

        List<ProductionCompany> managed = repo.findByManagerId("mgr-1");
        assertEquals(1, managed.size());
        assertEquals("co-3", managed.get(0).getId());
    }

    @Test
    void givenCompany_whenFindByManagerIdForNonMember_thenReturnsEmpty() {
        repo.save(new ProductionCompany("co-4", "Solo", "Desc", "founder-1"));

        assertTrue(repo.findByManagerId("outsider").isEmpty());
    }

    @Test
    void givenCompany_whenDeleteById_thenFindByIdReturnsEmpty() {
        repo.save(new ProductionCompany("co-5", "Delete Me", "Desc", "f-1"));

        repo.deleteById("co-5");

        assertTrue(repo.findById("co-5").isEmpty());
    }
}
