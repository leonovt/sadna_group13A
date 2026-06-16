package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({ActiveOrderRepositoryImpl.class, PersistenceConfig.class})
class ActiveOrderRepositoryImplTest {

    @Autowired
    private ActiveOrderRepositoryImpl repo;

    @Test
    void givenOrder_whenSave_thenFindByIdReturnsIt() {
        ActiveOrder order = new ActiveOrder("ord-1", "user-1");
        repo.save(order);

        Optional<ActiveOrder> found = repo.findById("ord-1");
        assertTrue(found.isPresent());
        assertEquals("user-1", found.get().getUserId());
    }

    @Test
    void givenNoOrder_whenFindById_thenReturnsEmpty() {
        assertTrue(repo.findById("ghost").isEmpty());
    }

    @Test
    void givenOrder_whenFindActiveByUserId_thenReturnsIt() {
        ActiveOrder order = new ActiveOrder(UUID.randomUUID().toString(), "user-A");
        repo.save(order);

        Optional<ActiveOrder> found = repo.findActiveByUserId("user-A");
        assertTrue(found.isPresent());
        assertEquals("user-A", found.get().getUserId());
    }

    @Test
    void givenNoOrderForUser_whenFindActiveByUserId_thenReturnsEmpty() {
        assertTrue(repo.findActiveByUserId("nobody").isEmpty());
    }

    @Test
    void givenOrder_whenDeleteById_thenFindByIdReturnsEmpty() {
        String orderId = UUID.randomUUID().toString();
        repo.save(new ActiveOrder(orderId, "user-B"));

        repo.deleteById(orderId);

        assertTrue(repo.findById(orderId).isEmpty());
    }

    @Test
    void givenTwoOrdersForDifferentUsers_whenFindActiveByUserId_thenReturnsCorrectOne() {
        ActiveOrder orderA = new ActiveOrder(UUID.randomUUID().toString(), "user-C");
        ActiveOrder orderB = new ActiveOrder(UUID.randomUUID().toString(), "user-D");
        repo.save(orderA);
        repo.save(orderB);

        Optional<ActiveOrder> found = repo.findActiveByUserId("user-C");
        assertTrue(found.isPresent());
        assertEquals("user-C", found.get().getUserId());
    }

    @Test
    void givenNoExistingOrder_whenGetOrCreate_thenCreatesAndPersistsNewOrder() {
        ActiveOrder created = repo.getOrCreate("user-E", () -> new ActiveOrder(UUID.randomUUID().toString(), "user-E"));

        assertEquals("user-E", created.getUserId());
        assertTrue(repo.findById(created.getId()).isPresent());
    }

    @Test
    void givenExistingOrder_whenGetOrCreate_thenReturnsExistingWithoutInvokingFactory() {
        ActiveOrder existing = new ActiveOrder(UUID.randomUUID().toString(), "user-F");
        repo.save(existing);

        ActiveOrder result = repo.getOrCreate("user-F", () -> {
            throw new AssertionError("factory should not be invoked when an order already exists");
        });

        assertEquals(existing.getId(), result.getId());
    }
}
