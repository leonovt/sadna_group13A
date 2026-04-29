package com.sadna.group13a.domain.event;

import com.sadna.group13a.domain.shared.SeatStatus;
import com.sadna.group13a.domain.shared.SeatUnavailableException;
import com.sadna.group13a.domain.shared.ZoneType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Zone domain entity.
 * Covers both SEATED and STANDING zone types.
 */
@DisplayName("Zone Entity Tests")
class ZoneTest {

    // ── SEATED Zone Construction ──────────────────────────────────

    @Nested
    @DisplayName("Seated Zone Construction")
    class SeatedConstructionTests {

        @Test
        @DisplayName("Given valid seated zone params — When creating — Then zone is SEATED with seats")
        void GivenValidParams_WhenCreating_ThenSeatedZone() {
            List<Seat> seats = Arrays.asList(new Seat("s1", "A-1"), new Seat("s2", "A-2"));
            SeatedZone zone = new SeatedZone("z1", "VIP", 100.0, seats);

            assertEquals("z1", zone.getId());
            assertEquals("VIP", zone.getName());
            assertEquals(ZoneType.SEATED, zone.getType());
            assertEquals(100.0, zone.getBasePrice());
            assertEquals(2, zone.getSeats().size());
            assertEquals(2, zone.getMaxCapacity());
        }

        @Test
        @DisplayName("Given empty seats list — When creating seated zone — Then throws")
        void GivenEmptySeats_WhenCreating_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SeatedZone("z1", "VIP", 100.0, List.of()));
        }

        @Test
        @DisplayName("Given null id — When creating — Then throws")
        void GivenNullId_WhenCreating_ThenThrows() {
            List<Seat> seats = List.of(new Seat("s1", "A-1"));
            assertThrows(IllegalArgumentException.class,
                    () -> new SeatedZone(null, "VIP", 100.0, seats));
        }

        @Test
        @DisplayName("Given negative price — When creating — Then throws")
        void GivenNegativePrice_WhenCreating_ThenThrows() {
            List<Seat> seats = List.of(new Seat("s1", "A-1"));
            assertThrows(IllegalArgumentException.class,
                    () -> new SeatedZone("z1", "VIP", -1.0, seats));
        }
    }

    // ── STANDING Zone Construction ────────────────────────────────

    @Nested
    @DisplayName("Standing Zone Construction")
    class StandingConstructionTests {

        @Test
        @DisplayName("Given valid standing zone params — When creating — Then zone is STANDING")
        void GivenValidParams_WhenCreating_ThenStandingZone() {
            StandingZone zone = new StandingZone("z2", "General", 50.0, 500);

            assertEquals(ZoneType.STANDING, zone.getType());
            assertEquals(500, zone.getMaxCapacity());
        }

        @Test
        @DisplayName("Given zero capacity — When creating standing zone — Then throws")
        void GivenZeroCapacity_WhenCreating_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new StandingZone("z2", "General", 50.0, 0));
        }
    }

    // ── SEATED Zone Operations ────────────────────────────────────

    @Nested
    @DisplayName("Seated Zone Operations")
    class SeatedOperationTests {

        @Test
        @DisplayName("Given seated zone — When finding seat by id — Then returns seat")
        void GivenSeatedZone_WhenFindSeat_ThenReturns() {
            Seat seat = new Seat("s1", "A-1");
            SeatedZone zone = new SeatedZone("z1", "VIP", 100.0, List.of(seat));

            assertTrue(zone.findSeatById("s1").isPresent());
            assertEquals("A-1", zone.findSeatById("s1").get().getLabel());
        }

        @Test
        @DisplayName("Given seated zone — When finding nonexistent seat — Then empty")
        void GivenSeatedZone_WhenFindNonexistent_ThenEmpty() {
            SeatedZone zone = new SeatedZone("z1", "VIP", 100.0,
                    List.of(new Seat("s1", "A-1")));

            assertTrue(zone.findSeatById("s999").isEmpty());
        }

        @Test
        @DisplayName("Given seated zone with all available — When checking count — Then all available")
        void GivenAllAvailable_WhenCheckingCount_ThenAllAvailable() {
            List<Seat> seats = Arrays.asList(
                    new Seat("s1", "A-1"), new Seat("s2", "A-2"),
                    new Seat("s3", "A-3"));
            SeatedZone zone = new SeatedZone("z1", "VIP", 100.0, seats);

            assertEquals(3, zone.getAvailableSeatCount());
        }

        @Test
        @DisplayName("Given seated zone with held seat — When checking count — Then one fewer")
        void GivenHeldSeat_WhenCheckingCount_ThenOneFewer() {
            Seat s1 = new Seat("s1", "A-1");
            Seat s2 = new Seat("s2", "A-2");
            SeatedZone zone = new SeatedZone("z1", "VIP", 100.0, Arrays.asList(s1, s2));

            s1.hold("user-1");

            assertEquals(1, zone.getAvailableSeatCount());
            assertEquals(1, zone.getActiveHoldCount());
        }
    }

    // ── STANDING Zone Operations ──────────────────────────────────

    @Nested
    @DisplayName("Standing Zone Operations")
    class StandingOperationTests {

        @Test
        @DisplayName("Given standing zone with capacity — When holding spot — Then hold succeeds")
        void GivenCapacity_WhenHold_ThenSucceeds() {
            StandingZone zone = new StandingZone("z1", "General", 50.0, 2);

            zone.holdStandingSpot("user-1");

            assertEquals(1, zone.getActiveHoldCount());
            assertEquals(1, zone.getAvailableSeatCount());
        }

        @Test
        @DisplayName("Given full standing zone — When holding — Then throws")
        void GivenFullZone_WhenHold_ThenThrows() {
            StandingZone zone = new StandingZone("z1", "General", 50.0, 1);
            zone.holdStandingSpot("user-1");

            assertThrows(SeatUnavailableException.class,
                    () -> zone.holdStandingSpot("user-2"));
        }

        @Test
        @DisplayName("Given standing hold — When selling — Then sold count increments")
        void GivenHold_WhenSell_ThenSoldIncremented() {
            StandingZone zone = new StandingZone("z1", "General", 50.0, 2);
            zone.holdStandingSpot("user-1");

            zone.sellStandingSpot("user-1");

            assertEquals(1, zone.getSoldCount());
            assertEquals(0, zone.getActiveHoldCount());
            assertEquals(1, zone.getAvailableSeatCount());
        }

        @Test
        @DisplayName("Given multiple standing holds for same user — When selling one — Then only one is sold and one remains held")
        void GivenMultipleHolds_WhenSellOne_ThenOneSoldOneHeld() {
            StandingZone zone = new StandingZone("z1", "General", 50.0, 5);
            zone.holdStandingSpot("user-1");
            zone.holdStandingSpot("user-1");

            assertEquals(2, zone.getActiveHoldCount());

            zone.sellStandingSpot("user-1");

            assertEquals(1, zone.getSoldCount());
            assertEquals(1, zone.getActiveHoldCount());
        }

        @Test
        @DisplayName("Given no hold — When selling — Then throws")
        void GivenNoHold_WhenSell_ThenThrows() {
            StandingZone zone = new StandingZone("z1", "General", 50.0, 2);

            assertThrows(SeatUnavailableException.class,
                    () -> zone.sellStandingSpot("user-1"));
        }

        @Test
        @DisplayName("Given standing hold — When releasing — Then capacity freed")
        void GivenHold_WhenRelease_ThenCapacityFreed() {
            StandingZone zone = new StandingZone("z1", "General", 50.0, 1);
            zone.holdStandingSpot("user-1");
            assertEquals(0, zone.getAvailableSeatCount());

            zone.releaseStandingSpot("user-1");
            assertEquals(1, zone.getAvailableSeatCount());
        }

        @Test
        @DisplayName("Given multiple standing holds for same user — When releasing one — Then one is released and one remains held")
        void GivenMultipleHolds_WhenReleaseOne_ThenOneReleasedOneHeld() {
            StandingZone zone = new StandingZone("z1", "General", 50.0, 5);
            zone.holdStandingSpot("user-1");
            zone.holdStandingSpot("user-1");

            assertEquals(2, zone.getActiveHoldCount());

            zone.releaseStandingSpot("user-1");

            assertEquals(1, zone.getActiveHoldCount());
            assertEquals(0, zone.getSoldCount());
        }

        @Test
        @DisplayName("Given expired standing hold — When new user holds — Then succeeds")
        void GivenExpiredHold_WhenNewHold_ThenSucceeds() {
            StandingZone zone = new StandingZone("z1", "General", 50.0, 1);
            zone.holdStandingSpot("user-1", Duration.ofMillis(1));

            try { Thread.sleep(5); } catch (InterruptedException ignored) {}

            // Expired hold should free capacity
            assertDoesNotThrow(() -> zone.holdStandingSpot("user-2"));
        }

        @Test
        @DisplayName("Given seated zone — When calling holdStandingSpot — Then throws UnsupportedOperation")
        void GivenSeatedZone_WhenHoldStanding_ThenThrows() {
            // this test is no longer applicable since holdStandingSpot is not on Zone or SeatedZone anymore.
        }
    }
}
