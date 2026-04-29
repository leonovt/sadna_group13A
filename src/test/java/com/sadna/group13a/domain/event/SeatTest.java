package com.sadna.group13a.domain.event;

import com.sadna.group13a.domain.shared.SeatStatus;
import com.sadna.group13a.domain.shared.SeatUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Seat domain entity.
 * Covers the 10-minute hold mechanism: hold, release, sell, and lazy expiry.
 */
@DisplayName("Seat Entity Tests")
class SeatTest {

    private Seat seat;

    @BeforeEach
    void setUp() {
        seat = new Seat("seat-1", "A-12");
    }

    // ── Construction ──────────────────────────────────────────────

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Given valid params — When creating seat — Then seat is AVAILABLE")
        void GivenValidParams_WhenCreating_ThenSeatIsAvailable() {
            assertEquals("seat-1", seat.getId());
            assertEquals("A-12", seat.getLabel());
            assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
            assertEquals(SeatStatus.AVAILABLE, seat.getEffectiveStatus());
            assertNull(seat.getHeldByUserId());
            assertNull(seat.getHoldExpiresAt());
        }

        @Test
        @DisplayName("Given auto-generated id — When creating seat — Then id is not blank")
        void GivenAutoId_WhenCreating_ThenIdNotBlank() {
            Seat auto = new Seat("B-3");
            assertNotNull(auto.getId());
            assertFalse(auto.getId().isBlank());
            assertEquals("B-3", auto.getLabel());
        }

        @Test
        @DisplayName("Given null id — When creating seat — Then throws exception")
        void GivenNullId_WhenCreating_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Seat(null, "A-1"));
        }

        @Test
        @DisplayName("Given blank label — When creating seat — Then throws exception")
        void GivenBlankLabel_WhenCreating_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Seat("id", "  "));
        }
    }

    // ── Hold ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Hold Scenarios")
    class HoldTests {

        @Test
        @DisplayName("Given available seat — When hold — Then status is HELD")
        void GivenAvailable_WhenHold_ThenHeld() {
            seat.hold("user-1");

            assertEquals(SeatStatus.HELD, seat.getStatus());
            assertEquals(SeatStatus.HELD, seat.getEffectiveStatus());
            assertEquals("user-1", seat.getHeldByUserId());
            assertNotNull(seat.getHoldExpiresAt());
        }

        @Test
        @DisplayName("Given held seat — When another user holds — Then throws SeatUnavailableException")
        void GivenHeld_WhenAnotherHolds_ThenThrows() {
            seat.hold("user-1");

            assertThrows(SeatUnavailableException.class,
                    () -> seat.hold("user-2"));
        }

        @Test
        @DisplayName("Given sold seat — When hold — Then throws SeatUnavailableException")
        void GivenSold_WhenHold_ThenThrows() {
            seat.hold("user-1");
            seat.sell("user-1");

            assertThrows(SeatUnavailableException.class,
                    () -> seat.hold("user-2"));
        }

        @Test
        @DisplayName("Given null userId — When hold — Then throws IllegalArgumentException")
        void GivenNullUserId_WhenHold_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> seat.hold(null));
        }

        @Test
        @DisplayName("Given zero duration — When hold — Then throws IllegalArgumentException")
        void GivenZeroDuration_WhenHold_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> seat.hold("user-1", Duration.ZERO));
        }

        @Test
        @DisplayName("Given expired hold — When new user holds — Then succeeds (lazy expiry)")
        void GivenExpiredHold_WhenNewUserHolds_ThenSucceeds() {
            // Hold with 1ms duration — will expire almost immediately
            seat.hold("user-1", Duration.ofMillis(1));

            // Small delay to ensure expiry
            try { Thread.sleep(5); } catch (InterruptedException ignored) {}

            // Effective status should be AVAILABLE due to lazy expiry
            assertEquals(SeatStatus.AVAILABLE, seat.getEffectiveStatus());

            // Another user should be able to hold
            assertDoesNotThrow(() -> seat.hold("user-2"));
            assertEquals(SeatStatus.HELD, seat.getEffectiveStatus());
            assertEquals("user-2", seat.getHeldByUserId());
        }
    }

    // ── Release ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Release Scenarios")
    class ReleaseTests {

        @Test
        @DisplayName("Given held seat — When release — Then status is AVAILABLE")
        void GivenHeld_WhenRelease_ThenAvailable() {
            seat.hold("user-1");
            seat.release();

            assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
            assertNull(seat.getHeldByUserId());
            assertNull(seat.getHoldExpiresAt());
        }

        @Test
        @DisplayName("Given available seat — When release — Then no error")
        void GivenAvailable_WhenRelease_ThenNoError() {
            assertDoesNotThrow(() -> seat.release());
            assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
        }

        @Test
        @DisplayName("Given sold seat — When release — Then throws SeatUnavailableException")
        void GivenSold_WhenRelease_ThenThrows() {
            seat.hold("user-1");
            seat.sell("user-1");

            assertThrows(SeatUnavailableException.class, () -> seat.release());
        }
    }

    // ── Sell ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sell Scenarios")
    class SellTests {

        @Test
        @DisplayName("Given held seat — When correct user sells — Then status is SOLD")
        void GivenHeld_WhenCorrectUserSells_ThenSold() {
            seat.hold("user-1");
            seat.sell("user-1");

            assertEquals(SeatStatus.SOLD, seat.getStatus());
            assertEquals("user-1", seat.getHeldByUserId());
            assertNull(seat.getHoldExpiresAt());
        }

        @Test
        @DisplayName("Given held seat — When different user sells — Then throws")
        void GivenHeld_WhenDifferentUserSells_ThenThrows() {
            seat.hold("user-1");

            assertThrows(SeatUnavailableException.class,
                    () -> seat.sell("user-2"));
        }

        @Test
        @DisplayName("Given available seat — When sell — Then throws")
        void GivenAvailable_WhenSell_ThenThrows() {
            assertThrows(SeatUnavailableException.class,
                    () -> seat.sell("user-1"));
        }

        @Test
        @DisplayName("Given expired hold — When sell — Then throws (lazy expiry)")
        void GivenExpiredHold_WhenSell_ThenThrows() {
            seat.hold("user-1", Duration.ofMillis(1));
            try { Thread.sleep(5); } catch (InterruptedException ignored) {}

            assertThrows(SeatUnavailableException.class,
                    () -> seat.sell("user-1"));
        }
    }
}
