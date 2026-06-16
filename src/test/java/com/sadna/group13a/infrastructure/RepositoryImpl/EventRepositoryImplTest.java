package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({EventRepositoryImpl.class, PersistenceConfig.class})
class EventRepositoryImplTest {

    @Autowired
    private EventRepositoryImpl repo;

    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(30);

    private Event buildEvent(String id, String companyId, String title, String category) {
        return new Event(id, title, "Desc", companyId, FUTURE, category);
    }

    private Event buildPublishedEvent(String id, String companyId) {
        Event event = buildEvent(id, companyId, "Concert", "Music");
        VenueMap vm = new VenueMap("vm-" + id, "Arena");
        vm.addZone(new SeatedZone("z-1", "VIP", 100.0, List.of(new Seat("s-1", "A-1"))));
        event.setVenueMap(vm);
        event.publish();
        return event;
    }

    @Test
    void givenEvent_whenSave_thenFindByIdReturnsIt() {
        Event event = buildEvent("ev-1", "co-1", "Rock Night", "Music");
        repo.save(event);

        Optional<Event> found = repo.findById("ev-1");
        assertTrue(found.isPresent());
        assertEquals("Rock Night", found.get().getTitle());
    }

    @Test
    void givenNoEvent_whenFindById_thenReturnsEmpty() {
        assertTrue(repo.findById("ghost").isEmpty());
    }

    @Test
    void givenPublishedEvent_whenFindPublished_thenReturnsIt() {
        repo.save(buildPublishedEvent("ev-2", "co-1"));

        List<Event> published = repo.findPublished();
        assertEquals(1, published.size());
        assertTrue(published.get(0).isPublished());
    }

    @Test
    void givenUnpublishedEvent_whenFindPublished_thenReturnsEmpty() {
        repo.save(buildEvent("ev-3", "co-1", "Unpublished", "Music"));

        assertTrue(repo.findPublished().isEmpty());
    }

    @Test
    void givenEvent_whenSearchByTitle_thenReturnsMatchingEvent() {
        repo.save(buildEvent("ev-4", "co-1", "Jazz Festival", "Music"));
        repo.save(buildEvent("ev-5", "co-1", "Rock Night", "Music"));

        List<Event> results = repo.searchByTitle("jazz");
        assertEquals(1, results.size());
        assertEquals("Jazz Festival", results.get(0).getTitle());
    }

    @Test
    void givenEvents_whenFindByCategory_thenReturnsMatchingEvents() {
        repo.save(buildEvent("ev-6", "co-1", "Jazz", "Music"));
        repo.save(buildEvent("ev-7", "co-1", "Football Match", "Sports"));

        List<Event> music = repo.findByCategory("Music");
        assertEquals(1, music.size());
        assertEquals("Music", music.get(0).getCategory());
    }

    @Test
    void givenEvents_whenFindByCompanyId_thenReturnsOnlyThatCompany() {
        repo.save(buildEvent("ev-8", "co-1", "Event A", "Music"));
        repo.save(buildEvent("ev-9", "co-2", "Event B", "Sports"));

        List<Event> co1Events = repo.findByCompanyId("co-1");
        assertEquals(1, co1Events.size());
        assertEquals("co-1", co1Events.get(0).getCompanyId());
    }

    @Test
    void givenEvent_whenDelete_thenFindByIdReturnsEmpty() {
        repo.save(buildEvent("ev-10", "co-1", "Gone", "Music"));

        repo.delete("ev-10");

        assertTrue(repo.findById("ev-10").isEmpty());
    }
}
