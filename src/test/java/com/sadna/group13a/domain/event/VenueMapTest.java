package com.sadna.group13a.domain.event;

import com.sadna.group13a.domain.shared.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the VenueMap domain entity.
 * Covers construction, zone management, and capacity queries.
 */
@DisplayName("VenueMap Entity Tests")
class VenueMapTest {

    private Zone seatedZone;
    private Zone standingZone;

    @BeforeEach
    void setUp() {
        seatedZone = new SeatedZone("z1", "VIP", 120.0,
                Arrays.asList(new Seat("s1", "A-1"), new Seat("s2", "A-2")));
        standingZone = new StandingZone("z2", "General Admission", 40.0, 100);
    }

    // ── Construction ──────────────────────────────────────────────

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Given valid params with zones — When creating — Then venue map has zones")
        void GivenValidParams_WhenCreating_ThenHasZones() {
            VenueMap map = new VenueMap("vm1", "Arena", List.of(seatedZone));

            assertEquals("vm1", map.getId());
            assertEquals("Arena", map.getVenueName());
            assertEquals(1, map.getZones().size());
        }

        @Test
        @DisplayName("Given valid params without zones — When creating — Then venue map is empty")
        void GivenValidParamsNoZones_WhenCreating_ThenEmpty() {
            VenueMap map = new VenueMap("vm1", "Arena");

            assertEquals(0, map.getZones().size());
        }

        @Test
        @DisplayName("Given null id — When creating — Then throws")
        void GivenNullId_WhenCreating_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new VenueMap(null, "Arena"));
        }

        @Test
        @DisplayName("Given blank venue name — When creating — Then throws")
        void GivenBlankName_WhenCreating_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new VenueMap("vm1", "  "));
        }

        @Test
        @DisplayName("Given empty zones list — When creating with zones constructor — Then throws")
        void GivenEmptyZones_WhenCreating_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new VenueMap("vm1", "Arena", List.of()));
        }
    }

    // ── Zone Management ───────────────────────────────────────────

    @Nested
    @DisplayName("Zone Management")
    class ZoneManagementTests {

        @Test
        @DisplayName("Given empty venue map — When adding zone — Then zone is added")
        void GivenEmptyMap_WhenAddZone_ThenAdded() {
            VenueMap map = new VenueMap("vm1", "Arena");
            map.addZone(seatedZone);

            assertEquals(1, map.getZones().size());
            assertEquals("VIP", map.getZones().get(0).getName());
        }

        @Test
        @DisplayName("Given null zone — When adding — Then throws")
        void GivenNullZone_WhenAdding_ThenThrows() {
            VenueMap map = new VenueMap("vm1", "Arena");
            assertThrows(IllegalArgumentException.class, () -> map.addZone(null));
        }

        @Test
        @DisplayName("Given venue map — When getting zone by valid id — Then returns zone")
        void GivenMap_WhenGetZoneById_ThenReturns() {
            VenueMap map = new VenueMap("vm1", "Arena",
                    Arrays.asList(seatedZone, standingZone));

            Zone found = map.getZoneById("z2");
            assertEquals("General Admission", found.getName());
        }

        @Test
        @DisplayName("Given venue map — When getting zone by invalid id — Then throws EntityNotFoundException")
        void GivenMap_WhenGetInvalidZoneId_ThenThrows() {
            VenueMap map = new VenueMap("vm1", "Arena", List.of(seatedZone));

            assertThrows(EntityNotFoundException.class,
                    () -> map.getZoneById("z999"));
        }

        @Test
        @DisplayName("Given venue map — When getting zones — Then list is unmodifiable")
        void GivenMap_WhenGetZones_ThenUnmodifiable() {
            VenueMap map = new VenueMap("vm1", "Arena", List.of(seatedZone));

            assertThrows(UnsupportedOperationException.class,
                    () -> map.getZones().add(standingZone));
        }
    }

    // ── Capacity Queries ──────────────────────────────────────────

    @Nested
    @DisplayName("Capacity Queries")
    class CapacityTests {

        @Test
        @DisplayName("Given mixed zones — When getting total capacity — Then sums all zones")
        void GivenMixedZones_WhenGetTotalCapacity_ThenSumsAll() {
            VenueMap map = new VenueMap("vm1", "Arena",
                    Arrays.asList(seatedZone, standingZone));

            // 2 seats + 100 standing = 102
            assertEquals(102, map.getTotalCapacity());
        }

        @Test
        @DisplayName("Given all available — When getting total available — Then equals capacity")
        void GivenAllAvailable_WhenGetTotalAvailable_ThenEqualsCapacity() {
            VenueMap map = new VenueMap("vm1", "Arena",
                    Arrays.asList(seatedZone, standingZone));

            assertEquals(102, map.getTotalAvailable());
        }

        @Test
        @DisplayName("Given some held seats — When getting total available — Then less than capacity")
        void GivenSomeHeld_WhenGetTotalAvailable_ThenLess() {
            VenueMap map = new VenueMap("vm1", "Arena",
                    Arrays.asList(seatedZone, standingZone));

            ((SeatedZone) seatedZone).getSeats().get(0).hold("user-1");

            assertEquals(101, map.getTotalAvailable());
        }
    }
}
