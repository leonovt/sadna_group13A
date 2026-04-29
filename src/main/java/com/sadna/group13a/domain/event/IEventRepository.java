package com.sadna.group13a.domain.event;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for the Event aggregate.
 * Defined in the Domain layer; implemented in Infrastructure layer.
 */
public interface IEventRepository {

    /** Find an event by its unique ID. */
    Optional<Event> findById(String id);

    /** Persist an event (create or update). */
    void save(Event event);

    /** Remove an event from the repository. */
    void delete(String id);

    /** Return all events in the system. */
    List<Event> findAll();

    /** Find all events owned by a specific company. */
    List<Event> findByCompanyId(String companyId);

    /** Find all published events (visible to buyers). */
    List<Event> findPublished();

    /** Search events by title (case-insensitive substring match). */
    List<Event> searchByTitle(String query);

    /** Search events by category. */
    List<Event> findByCategory(String category);
}
