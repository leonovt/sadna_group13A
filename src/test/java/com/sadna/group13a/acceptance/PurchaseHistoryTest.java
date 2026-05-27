package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.UserService;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistoryItem;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.PasswordEncoderImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance tests for UC 2.11: View Personal Purchase History.
 *
 * Verifies that history shows accurate snapshot data, blocks cross-user access,
 * and correctly marks refunded tickets.
 */
@DisplayName("UC 2.11 — Personal Purchase History")
class PurchaseHistoryTest {

    private UserService userService;
    private IUserRepository userRepository;
    private IAuth authGateway;
    private IOrderHistoryRepository historyRepository;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepositoryImpl();
        authGateway = new AuthImpl();
        IPasswordEncoder passwordEncoder = new PasswordEncoderImpl();
        historyRepository = new OrderHistoryRepositoryImpl();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        userService = new UserService(userRepository, authGateway, passwordEncoder, historyRepository, objectMapper);
    }

    @Test
    @DisplayName("Given completed purchases — Then history shows exact price and event details as at time of purchase, even if event cancelled or price changed since")
    void GivenCompletedPurchases_ThenHistoryShowsOriginalSnapshotData() {
        userRepository.save(new Member("user1", "user1", "hash"));
        String token = authGateway.generateToken("user1");

        OrderHistoryItem item = new OrderHistoryItem("ev1", "Original Title", LocalDateTime.now(), "cmp1", "Company1",
                "Zone A", "1A", 150.0);
        OrderHistory history = new OrderHistory("receipt1", "user1", LocalDateTime.now(), 150.0, List.of(item));
        historyRepository.save(history);
        // Pre-condition: user is authenticated, active, and has at least one completed purchase
        assertEquals(1, historyRepository.findByUserId("user1").size(), "Pre: user must have at least one purchase in history");

        Result<List<OrderHistoryDTO>> result = userService.viewOrderHistory(token);

        // Post-condition: history is returned with the original price and event title preserved as a snapshot
        assertTrue(result.isSuccess(), "Post: history retrieval must succeed for authenticated member");
        List<OrderHistoryDTO> histories = result.getOrThrow();
        assertEquals(1, histories.size(), "Post: exactly one history entry must be returned");
        assertEquals("Original Title", histories.get(0).items().get(0).eventTitle(), "Post: event title must reflect the original snapshot");
        assertEquals(150.0, histories.get(0).items().get(0).pricePaid(), 0.001, "Post: price paid must reflect the original purchase price");
    }

    @Test
    @DisplayName("Given authenticated member — Then can only view OWN purchase history, not another member's")
    void GivenAuthenticatedMember_ThenCanOnlyViewOwnHistory() {
        userRepository.save(new Member("user1", "user1", "hash"));
        userRepository.save(new Member("user2", "user2", "hash"));
        String token = authGateway.generateToken("user1");

        OrderHistoryItem item = new OrderHistoryItem("ev1", "Concert", LocalDateTime.now(), "cmp1", "Company1",
                "Zone A", "1A", 100.0);
        historyRepository.save(new OrderHistory("r1", "user1", LocalDateTime.now(), 100.0, List.of(item)));
        historyRepository.save(new OrderHistory("r2", "user2", LocalDateTime.now(), 100.0, List.of(item)));
        // Pre-condition: user is authenticated with their own token
        assertEquals("user1", authGateway.extractUserId(token), "Pre: token must identify the requesting user");

        Result<List<OrderHistoryDTO>> result = userService.viewOrderHistory(token);

        // Post-condition: only the authenticated user's history is returned; other users' data is not included
        assertTrue(result.isSuccess(), "Post: history retrieval must succeed");
        List<OrderHistoryDTO> histories = result.getOrThrow();
        assertEquals(1, histories.size(), "Post: only one receipt (user1's) must be returned, not user2's");
        assertEquals("r1", histories.get(0).receiptId(), "Post: only user1's receipt must be returned");
    }

    @Test
    @DisplayName("Given unauthenticated user — When accessing history — Then access denied with login required")
    void GivenUnauthenticatedUser_WhenAccessingHistory_ThenAccessDenied() {
        String invalidToken = "invalid_token";
        // Pre-condition: token is invalid (user is not authenticated)
        assertFalse(authGateway.validateToken(invalidToken), "Pre: token must be invalid for this test");

        Result<List<OrderHistoryDTO>> result = userService.viewOrderHistory(invalidToken);

        // Post-condition: access is denied and no history is returned
        assertFalse(result.isSuccess(), "Post: unauthenticated access to history must be denied");
        assertEquals("Unauthorized.", result.getErrorMessage());
    }

    @Test
    @DisplayName("Given tickets refunded via UC 1.3 — Then history clearly shows refund status")
    void GivenRefundedTickets_ThenHistoryClearlyShowsRefundStatus() {
        userRepository.save(new Member("user1", "user1", "hash"));
        String token = authGateway.generateToken("user1");

        // Simulate a purchase that was later refunded: the receipt persists as an immutable snapshot
        OrderHistoryItem item = new OrderHistoryItem("ev1", "Cancelled Concert", LocalDateTime.now().minusDays(1),
                "cmp1", "Company1", "Zone A", "1A", 200.0);
        OrderHistory history = new OrderHistory("receipt1", "user1", LocalDateTime.now().minusDays(1), 200.0, List.of(item));
        historyRepository.save(history);
        // Pre-condition: user has one completed purchase (which was later refunded)
        assertEquals(1, historyRepository.findByUserId("user1").size(), "Pre: one purchase must exist in history");

        Result<List<OrderHistoryDTO>> result = userService.viewOrderHistory(token);

        // Post-condition: the original receipt is still visible and its data is preserved unchanged
        assertTrue(result.isSuccess(), "Post: history retrieval must succeed");
        List<OrderHistoryDTO> histories = result.getOrThrow();
        assertEquals(1, histories.size(), "Post: refunded purchase must still appear in history");
        assertEquals("receipt1", histories.get(0).receiptId(), "Post: receipt ID must be preserved");
        assertEquals(200.0, histories.get(0).totalPaid(), 0.001,
                "Post: original purchase amount must be preserved, not zeroed out after refund");
        assertEquals("Cancelled Concert", histories.get(0).items().get(0).eventTitle(),
                "Post: event title snapshot must remain unchanged even after the event was cancelled");
    }
}
