package com.sadna.group13a.domain.Aggregates.Event;

import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.EntityNotFoundException;
import com.sadna.group13a.domain.shared.SeatUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Event aggregate: Event, VenueMap, SeatedZone, StandingZone, Seat.
 * No Spring, no Mockito — pure domain instantiation.
 */
class EventAggregateTest {

    private static final LocalDateTime FUTURE_DATE = LocalDateTime.now().plusDays(30);

    // ── Helpers ───────────────────────────────────────────────────

    private Event buildUnpublishedEvent() {
        return new Event(UUID.randomUUID().toString(), "Rock Concert", "A great show",
                "company-1", FUTURE_DATE, "Music");
    }

    private SeatedZone buildSeatedZone(int seatCount) {
        List<Seat> seats = new java.util.ArrayList<>();
        for (int i = 0; i < seatCount; i++) {
            seats.add(new Seat(UUID.randomUUID().toString(), "R" + (i + 1)));
        }
        return new SeatedZone(UUID.randomUUID().toString(), "VIP", 150.0, seats);
    }

    private VenueMap buildVenueMapWithOneSeatedZone() {
        VenueMap map = new VenueMap(UUID.randomUUID().toString(), "Main Hall");
        map.addZone(buildSeatedZone(5));
        return map;
    }

    // ══════════════════════════════════════════════════════════════
    // Event root
    // ══════════════════════════════════════════════════════════════

    @Nested
    class EventRootTests {

        @Test
        void givenValidParams_whenCreatingEvent_thenEventIsUnpublishedWithRegularMode() {
            Event event = buildUnpublishedEvent();

            assertFalse(event.isPublished());
            assertEquals(EventSaleMode.REGULAR, event.getSaleMode());
            assertNull(event.getVenueMap());
        }

        @Test
        void givenEventWithVenueMap_whenPublished_thenIsPublishedIsTrue() {
            Event event = buildUnpublishedEvent();
            event.setVenueMap(buildVenueMapWithOneSeatedZone());

            event.publish();

            assertTrue(event.isPublished());
        }

        @Test
        void givenEventWithoutVenueMap_whenPublished_thenThrowsDomainException() {
            Event event = buildUnpublishedEvent();

            assertThrows(DomainException.class, event::publish);
        }

        @Test
        void givenPublishedEvent_whenSettingVenueMap_thenThrowsDomainException() {
            Event event = buildUnpublishedEvent();
            event.setVenueMap(buildVenueMapWithOneSeatedZone());
            event.publish();

            assertThrows(DomainException.class,
                    () -> event.setVenueMap(buildVenueMapWithOneSeatedZone()));
        }

        @Test
        void givenPublishedEvent_whenChangingSaleMode_thenThrowsDomainException() {
            Event event = buildUnpublishedEvent();
            event.setVenueMap(buildVenueMapWithOneSeatedZone());
            event.publish();

            assertThrows(DomainException.class,
                    () -> event.setSaleMode(EventSaleMode.RAFFLE));
        }

        @Test
        void givenUnpublishedEvent_whenChangingSaleMode_thenModeIsUpdated() {
            Event event = buildUnpublishedEvent();

            event.setSaleMode(EventSaleMode.RAFFLE);

            assertEquals(EventSaleMode.RAFFLE, event.getSaleMode());
        }

        @Test
        void givenPublishedEvent_whenUnpublished_thenIsPublishedIsFalse() {
            Event event = buildUnpublishedEvent();
            event.setVenueMap(buildVenueMapWithOneSeatedZone());
            event.publish();

            event.unpublish();

            assertFalse(event.isPublished());
        }

        @Test
        void givenEventWithoutVenueMap_whenGetZoneById_thenThrowsDomainException() {
            Event event = buildUnpublishedEvent();

            assertThrows(DomainException.class, () -> event.getZoneById("z-1"));
        }

        @Test
        void givenEventWithoutVenueMap_whenGetTotalAvailable_thenThrowsDomainException() {
            Event event = buildUnpublishedEvent();

            assertThrows(DomainException.class, event::getTotalAvailable);
        }

        @Test
        void givenNullEventId_whenCreatingEvent_thenThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Event(null, "title", "desc", "co-1", FUTURE_DATE, "cat"));
        }

        @Test
        void givenBlankTitle_whenCreatingEvent_thenThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Event(UUID.randomUUID().toString(), "  ", "desc", "co-1", FUTURE_DATE, "cat"));
        }

        @Test
        void givenNullDate_whenCreatingEvent_thenThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Event(UUID.randomUUID().toString(), "title", "desc", "co-1", null, "cat"));
        }

        @Test
        void givenNullSaleMode_whenChangingSaleMode_thenThrowsIllegalArgumentException() {
            Event event = buildUnpublishedEvent();

            assertThrows(IllegalArgumentException.class, () -> event.setSaleMode(null));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // VenueMap
    // ══════════════════════════════════════════════════════════════

    @Nested
    class VenueMapTests {

        @Test
        void givenVenueMapWithZones_whenGetTotalCapacity_thenSumOfAllZoneCapacities() {
            SeatedZone zone1 = buildSeatedZone(3);
            SeatedZone zone2 = buildSeatedZone(5);
            VenueMap map = new VenueMap(UUID.randomUUID().toString(), "Arena",
                    List.of(zone1, zone2));

            assertEquals(8, map.getTotalCapacity());
        }

        @Test
        void givenVenueMap_whenGetZoneByExistingId_thenReturnsCorrectZone() {
            SeatedZone zone = buildSeatedZone(3);
            VenueMap map = new VenueMap(UUID.randomUUID().toString(), "Arena",
                    List.of(zone));

            Zone found = map.getZoneById(zone.getId());

            assertEquals(zone.getId(), found.getId());
        }

        @Test
        void givenVenueMap_whenGetZoneByMissingId_thenThrowsEntityNotFoundException() {
            VenueMap map = new VenueMap(UUID.randomUUID().toString(), "Arena",
                    List.of(buildSeatedZone(2)));

            assertThrows(EntityNotFoundException.class, () -> map.getZoneById("nonexistent-id"));
        }

        @Test
        void givenEmptyZonesList_whenCreatingVenueMap_thenThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new VenueMap(UUID.randomUUID().toString(), "Arena", List.of()));
        }

        @Test
        void givenVenueMapWithAddedZone_whenGetTotalAvailable_thenReflectsNewZone() {
            VenueMap map = new VenueMap(UUID.randomUUID().toString(), "Arena");
            map.addZone(buildSeatedZone(4));

            assertEquals(4, map.getTotalAvailable());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Seat
    // ══════════════════════════════════════════════════════════════

    @Nested
    class SeatTests {

        private Seat seat;

        @BeforeEach
        void setUp() {
            seat = new Seat(UUID.randomUUID().toString(), "A-1");
        }

        @Test
        void givenAvailableSeat_whenHeld_thenEffectiveStatusIsHeld() {
            seat.hold("user-1");

            assertEquals(SeatStatus.HELD, seat.getEffectiveStatus());
            assertEquals("user-1", seat.getHeldByUserId());
        }

        @Test
        void givenHeldSeat_whenHeldByAnotherUser_thenThrowsSeatUnavailableException() {
            seat.hold("user-1");

            assertThrows(SeatUnavailableException.class, () -> seat.hold("user-2"));
        }

        @Test
        void givenSoldSeat_whenHeld_thenThrowsSeatUnavailableException() {
            seat.hold("user-1");
            seat.sell("user-1");

            assertThrows(SeatUnavailableException.class, () -> seat.hold("user-2"));
        }

        @Test
        void givenHeldSeat_whenReleased_thenEffectiveStatusIsAvailable() {
            seat.hold("user-1");
            seat.release();

            assertEquals(SeatStatus.AVAILABLE, seat.getEffectiveStatus());
            assertNull(seat.getHeldByUserId());
        }

        @Test
        void givenSoldSeat_whenReleased_thenThrowsSeatUnavailableException() {
            seat.hold("user-1");
            seat.sell("user-1");

            assertThrows(SeatUnavailableException.class, seat::release);
        }

        @Test
        void givenHeldSeat_whenSoldBySameUser_thenStatusIsSold() {
            seat.hold("user-1");
            seat.sell("user-1");

            assertEquals(SeatStatus.SOLD, seat.getEffectiveStatus());
            assertNull(seat.getHoldExpiresAt());
        }

        @Test
        void givenHeldSeat_whenSoldByDifferentUser_thenThrowsSeatUnavailableException() {
            seat.hold("user-1");

            assertThrows(SeatUnavailableException.class, () -> seat.sell("user-2"));
        }

        @Test
        void givenAvailableSeat_whenSold_thenThrowsSeatUnavailableException() {
            assertThrows(SeatUnavailableException.class, () -> seat.sell("user-1"));
        }

        @Test
        void givenHeldSeatWithExpiredTtl_whenGetEffectiveStatus_thenReturnsAvailable() {
            seat.hold("user-1", Duration.ofMillis(1));
            // Spin briefly to let the hold expire
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}

            assertEquals(SeatStatus.AVAILABLE, seat.getEffectiveStatus());
        }

        @Test
        void givenNullSeatId_whenCreatingSeat_thenThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> new Seat(null, "A-1"));
        }

        @Test
        void givenBlankLabel_whenCreatingSeat_thenThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Seat(UUID.randomUUID().toString(), "  "));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // SeatedZone
    // ══════════════════════════════════════════════════════════════

    @Nested
    class SeatedZoneTests {

        @Test
        void givenSeatedZoneWithThreeSeats_whenAllAvailable_thenAvailableCountIsThree() {
            SeatedZone zone = buildSeatedZone(3);

            assertEquals(3, zone.getMaxCapacity());
            assertEquals(3, zone.getAvailableSeatCount());
            assertEquals(0, zone.getActiveHoldCount());
            assertEquals(0, zone.getSoldCount());
        }

        @Test
        void givenSeatedZone_whenOneSeatHeld_thenAvailableDecreasesAndHoldCountIncreases() {
            SeatedZone zone = buildSeatedZone(3);
            Seat seat = zone.getSeats().get(0);
            seat.hold("user-1");

            assertEquals(2, zone.getAvailableSeatCount());
            assertEquals(1, zone.getActiveHoldCount());
        }

        @Test
        void givenSeatedZone_whenOneSeatSold_thenSoldCountIncreasesAndAvailableDecreases() {
            SeatedZone zone = buildSeatedZone(3);
            Seat seat = zone.getSeats().get(0);
            seat.hold("user-1");
            seat.sell("user-1");

            assertEquals(2, zone.getAvailableSeatCount());
            assertEquals(1, zone.getSoldCount());
            assertEquals(0, zone.getActiveHoldCount());
        }

        @Test
        void givenSeatedZoneWithEmptySeatList_whenCreating_thenThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SeatedZone(UUID.randomUUID().toString(), "VIP", 100.0, List.of()));
        }

        @Test
        void givenSeatedZone_whenFindSeatByValidId_thenReturnsSeat() {
            Seat expected = new Seat(UUID.randomUUID().toString(), "B-3");
            SeatedZone zone = new SeatedZone(UUID.randomUUID().toString(), "Zone A", 50.0, List.of(expected));

            assertTrue(zone.findSeatById(expected.getId()).isPresent());
            assertEquals(expected.getId(), zone.findSeatById(expected.getId()).get().getId());
        }

        @Test
        void givenSeatedZone_whenFindSeatByInvalidId_thenReturnsEmpty() {
            SeatedZone zone = buildSeatedZone(2);

            assertTrue(zone.findSeatById("no-such-seat").isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // StandingZone
    // ══════════════════════════════════════════════════════════════

    @Nested
    class StandingZoneTests {

        @Test
        void givenStandingZoneWithCapacity5_whenAllAvailable_thenAvailableCountIs5() {
            StandingZone zone = new StandingZone(UUID.randomUUID().toString(), "GA", 25.0, 5);

            assertEquals(5, zone.getMaxCapacity());
            assertEquals(5, zone.getAvailableSeatCount());
        }

        @Test
        void givenStandingZone_whenOneSpotHeld_thenAvailableDecreasesByOne() {
            StandingZone zone = new StandingZone(UUID.randomUUID().toString(), "GA", 25.0, 3);
            zone.holdStandingSpot("user-1");

            assertEquals(2, zone.getAvailableSeatCount());
            assertEquals(1, zone.getActiveHoldCount());
        }

        @Test
        void givenFullStandingZone_whenAnotherHoldAttempted_thenThrowsSeatUnavailableException() {
            StandingZone zone = new StandingZone(UUID.randomUUID().toString(), "GA", 25.0, 2);
            zone.holdStandingSpot("user-1");
            zone.holdStandingSpot("user-2");

            assertThrows(SeatUnavailableException.class, () -> zone.holdStandingSpot("user-3"));
        }

        @Test
        void givenHeldStandingSpot_whenSold_thenSoldCountIncreasesAndHoldDecreases() {
            StandingZone zone = new StandingZone(UUID.randomUUID().toString(), "GA", 25.0, 3);
            zone.holdStandingSpot("user-1");
            zone.sellStandingSpot("user-1");

            assertEquals(1, zone.getSoldCount());
            assertEquals(0, zone.getActiveHoldCount());
            assertEquals(2, zone.getAvailableSeatCount());
        }

        @Test
        void givenNoHold_whenSellStandingSpot_thenThrowsSeatUnavailableException() {
            StandingZone zone = new StandingZone(UUID.randomUUID().toString(), "GA", 25.0, 3);

            assertThrows(SeatUnavailableException.class, () -> zone.sellStandingSpot("user-1"));
        }

        @Test
        void givenHeldSpot_whenReleased_thenAvailableRestored() {
            StandingZone zone = new StandingZone(UUID.randomUUID().toString(), "GA", 25.0, 3);
            zone.holdStandingSpot("user-1");
            zone.releaseStandingSpot("user-1");

            assertEquals(3, zone.getAvailableSeatCount());
        }

        @Test
        void givenZeroCapacity_whenCreatingStandingZone_thenThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new StandingZone(UUID.randomUUID().toString(), "GA", 25.0, 0));
        }

        @Test
        void givenNegativeBasePrice_whenCreatingZone_thenThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new StandingZone(UUID.randomUUID().toString(), "GA", -1.0, 10));
        }
    }
}
