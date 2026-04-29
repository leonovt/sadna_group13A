package com.sadna.group13a.domain.event;

import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Event aggregate root.
 * Covers construction, publishing lifecycle, venue map management,
 * and convenience delegations.
 */
@DisplayName("Event Aggregate Root Tests")
class EventTest {

    private Event event;
    private VenueMap venueMap;

    @BeforeEach
    void setUp() {
        event = new Event("evt-1", "Rock Concert", "A rock concert",
                "company-1", LocalDateTime.of(2026, 6, 15, 20, 0), "Music");

        Zone seatedZone = new SeatedZone("z1", "VIP", 120.0,
                Arrays.asList(new Seat("s1", "A-1"), new Seat("s2", "A-2")));
        Zone standingZone = new StandingZone("z2", "GA", 40.0, 100);
        venueMap = new VenueMap("vm1", "Arena", Arrays.asList(seatedZone, standingZone));
    }

    // ── Construction ──────────────────────────────────────────────

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Given valid params — When creating event — Then event is unpublished with no venue map")
        void GivenValidParams_WhenCreating_ThenUnpublished() {
            assertEquals("evt-1", event.getId());
            assertEquals("Rock Concert", event.getTitle());
            assertEquals("A rock concert", event.getDescription());
            assertEquals("company-1", event.getCompanyId());
            assertEquals("Music", event.getCategory());
            assertFalse(event.isPublished());
            assertNull(event.getVenueMap());
        }

        @Test
        @DisplayName("Given auto-generated id — When creating event — Then id is not blank")
        void GivenAutoId_WhenCreating_ThenIdNotBlank() {
            Event auto = new Event("Title", "Desc", "comp-1",
                    LocalDateTime.now().plusDays(1), "Category");
            assertNotNull(auto.getId());
            assertFalse(auto.getId().isBlank());
        }

        @Test
        @DisplayName("Given null title — When creating event — Then throws")
        void GivenNullTitle_WhenCreating_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Event("id", null, "desc", "comp-1",
                            LocalDateTime.now(), "cat"));
        }

        @Test
        @DisplayName("Given null company id — When creating event — Then throws")
        void GivenNullCompanyId_WhenCreating_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Event("id", "Title", "desc", null,
                            LocalDateTime.now(), "cat"));
        }

        @Test
        @DisplayName("Given null event date — When creating event — Then throws")
        void GivenNullEventDate_WhenCreating_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Event("id", "Title", "desc", "comp-1",
                            null, "cat"));
        }
    }

    // ── Property Setters ──────────────────────────────────────────

    @Nested
    @DisplayName("Property Updates")
    class PropertyTests {

        @Test
        @DisplayName("Given event — When setting valid title — Then title updated")
        void GivenEvent_WhenSetTitle_ThenUpdated() {
            event.setTitle("Jazz Night");
            assertEquals("Jazz Night", event.getTitle());
        }

        @Test
        @DisplayName("Given event — When setting blank title — Then throws")
        void GivenEvent_WhenSetBlankTitle_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> event.setTitle("  "));
        }

        @Test
        @DisplayName("Given event — When setting null date — Then throws")
        void GivenEvent_WhenSetNullDate_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> event.setEventDate(null));
        }

        @Test
        @DisplayName("Given event — When setting description — Then updated")
        void GivenEvent_WhenSetDescription_ThenUpdated() {
            event.setDescription("Updated description");
            assertEquals("Updated description", event.getDescription());
        }

        @Test
        @DisplayName("Given event — When setting category — Then updated")
        void GivenEvent_WhenSetCategory_ThenUpdated() {
            event.setCategory("Jazz");
            assertEquals("Jazz", event.getCategory());
        }
    }

    // ── VenueMap Management ───────────────────────────────────────

    @Nested
    @DisplayName("VenueMap Management")
    class VenueMapTests {

        @Test
        @DisplayName("Given unpublished event — When setting venue map — Then map is assigned")
        void GivenUnpublished_WhenSetVenueMap_ThenAssigned() {
            event.setVenueMap(venueMap);

            assertNotNull(event.getVenueMap());
            assertEquals("Arena", event.getVenueMap().getVenueName());
        }

        @Test
        @DisplayName("Given published event — When setting venue map — Then throws DomainException")
        void GivenPublished_WhenSetVenueMap_ThenThrows() {
            event.setVenueMap(venueMap);
            event.publish();

            VenueMap newMap = new VenueMap("vm2", "Stadium",
                    List.of(new StandingZone("z3", "Floor", 80.0, 200)));

            assertThrows(DomainException.class, () -> event.setVenueMap(newMap));
        }
    }

    // ── Publishing ────────────────────────────────────────────────

    @Nested
    @DisplayName("Publishing Lifecycle")
    class PublishingTests {

        @Test
        @DisplayName("Given event with venue map — When publish — Then event is published")
        void GivenEventWithVenueMap_WhenPublish_ThenPublished() {
            event.setVenueMap(venueMap);
            event.publish();

            assertTrue(event.isPublished());
        }

        @Test
        @DisplayName("Given event without venue map — When publish — Then throws DomainException")
        void GivenEventWithoutVenueMap_WhenPublish_ThenThrows() {
            assertThrows(DomainException.class, () -> event.publish());
        }

        @Test
        @DisplayName("Given published event — When unpublish — Then event is unpublished")
        void GivenPublished_WhenUnpublish_ThenUnpublished() {
            event.setVenueMap(venueMap);
            event.publish();
            event.unpublish();

            assertFalse(event.isPublished());
        }
    }

    // ── Convenience Delegations ───────────────────────────────────

    @Nested
    @DisplayName("Convenience Delegations")
    class DelegationTests {

        @Test
        @DisplayName("Given event with venue map — When getting zone by id — Then returns zone")
        void GivenVenueMap_WhenGetZoneById_ThenReturns() {
            event.setVenueMap(venueMap);

            Zone zone = event.getZoneById("z1");
            assertEquals("VIP", zone.getName());
        }

        @Test
        @DisplayName("Given event without venue map — When getting zone by id — Then throws")
        void GivenNoVenueMap_WhenGetZoneById_ThenThrows() {
            assertThrows(DomainException.class, () -> event.getZoneById("z1"));
        }

        @Test
        @DisplayName("Given event with venue map — When getting invalid zone — Then throws EntityNotFound")
        void GivenVenueMap_WhenGetInvalidZone_ThenThrows() {
            event.setVenueMap(venueMap);

            assertThrows(EntityNotFoundException.class,
                    () -> event.getZoneById("z999"));
        }

        @Test
        @DisplayName("Given event with venue map — When getting total available — Then returns correct count")
        void GivenVenueMap_WhenGetTotalAvailable_ThenCorrect() {
            event.setVenueMap(venueMap);

            // 2 seated + 100 standing = 102
            assertEquals(102, event.getTotalAvailable());
        }

        @Test
        @DisplayName("Given event without venue map — When getting total available — Then throws")
        void GivenNoVenueMap_WhenGetTotalAvailable_ThenThrows() {
            assertThrows(DomainException.class, () -> event.getTotalAvailable());
        }
    }
}
