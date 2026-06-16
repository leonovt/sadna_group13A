package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistoryItem;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({OrderHistoryRepositoryImpl.class, PersistenceConfig.class})
class OrderHistoryRepositoryImplTest {

    @Autowired
    private OrderHistoryRepositoryImpl repo;

    private OrderHistoryItem buildItem(String companyId) {
        return new OrderHistoryItem(
                "event-1", "Rock Concert", LocalDateTime.now().plusDays(10),
                companyId, "Acme Events", "VIP", "A-1", 100.0);
    }

    private OrderHistory buildHistory(String userId, String companyId) {
        return new OrderHistory(
                UUID.randomUUID().toString(), userId, LocalDateTime.now(),
                100.0, List.of(buildItem(companyId)));
    }

    @Test
    void givenOrderHistory_whenSave_thenFindByIdReturnsIt() {
        OrderHistory history = buildHistory("user-1", "co-1");
        repo.save(history);

        Optional<OrderHistory> found = repo.findById(history.getReceiptId());
        assertTrue(found.isPresent());
        assertEquals("user-1", found.get().getUserId());
    }

    @Test
    void givenNoHistory_whenFindById_thenReturnsEmpty() {
        assertTrue(repo.findById("ghost-receipt").isEmpty());
    }

    @Test
    void givenOrderHistory_whenFindByUserId_thenReturnsIt() {
        repo.save(buildHistory("user-A", "co-1"));
        repo.save(buildHistory("user-B", "co-1"));

        List<OrderHistory> userAHistory = repo.findByUserId("user-A");
        assertEquals(1, userAHistory.size());
        assertEquals("user-A", userAHistory.get(0).getUserId());
    }

    @Test
    void givenOrderHistory_whenFindByCompanyId_thenReturnsMatchingOrders() {
        repo.save(buildHistory("user-1", "company-X"));
        repo.save(buildHistory("user-2", "company-Y"));

        List<OrderHistory> xOrders = repo.findByCompanyId("company-X");
        assertEquals(1, xOrders.size());
        assertTrue(xOrders.get(0).containsItemFromCompany("company-X"));
    }

    @Test
    void givenTwoHistories_whenFindAll_thenReturnsBoth() {
        repo.save(buildHistory("user-1", "co-1"));
        repo.save(buildHistory("user-2", "co-2"));

        assertEquals(2, repo.findAll().size());
    }

    @Test
    void givenNoHistory_whenFindByUserId_thenReturnsEmptyList() {
        assertTrue(repo.findByUserId("nobody").isEmpty());
    }
}
