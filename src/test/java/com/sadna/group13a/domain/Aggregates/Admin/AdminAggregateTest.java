package com.sadna.group13a.domain.Aggregates.Admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminAggregateTest {

    @Test
    void givenValidParams_whenCreatingAdmin_thenGettersReturnExpectedValues() {
        Admin admin = new Admin("admin-id-1", "user-id-1");

        assertEquals("admin-id-1", admin.getId());
        assertEquals("user-id-1", admin.getUserId());
    }

    @Test
    void givenBlankId_whenCreatingAdmin_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Admin("  ", "user-id-1"));
    }

    @Test
    void givenNullId_whenCreatingAdmin_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Admin(null, "user-id-1"));
    }

    @Test
    void givenBlankUserId_whenCreatingAdmin_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Admin("admin-id-1", "  "));
    }

    @Test
    void givenNullUserId_whenCreatingAdmin_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Admin("admin-id-1", null));
    }

    @Test
    void givenTwoAdminsWithDifferentIds_whenComparing_thenTheyAreDistinctObjects() {
        Admin a1 = new Admin("a-1", "u-1");
        Admin a2 = new Admin("a-2", "u-2");

        assertNotEquals(a1.getId(), a2.getId());
        assertNotEquals(a1.getUserId(), a2.getUserId());
    }
}
