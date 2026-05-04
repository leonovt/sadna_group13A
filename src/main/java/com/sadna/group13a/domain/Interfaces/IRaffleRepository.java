package com.sadna.group13a.domain.Interfaces;

import java.util.List;
import java.util.Optional;

import com.sadna.group13a.domain.Aggregates.Raffle.Raffle;


/**
 * The Domain Repository interface for the Raffle aggregate.
 * The actual implementation will live in the Infrastructure layer (e.g., Spring Data JPA).
 */
public interface IRaffleRepository {
    
    /**
     * Saves a new Raffle or updates an existing one.
     * This will cascade and save the participant list and winning AuthorizationCodes automatically.
     */
    void save(Raffle raffle);
    
    /**
     * Retrieves a Raffle by its unique ID.
     */
    Optional<Raffle> findById(String id);
    
    /**
     * (Optional but helpful) Retrieves all raffles for a specific event.
     */
    List<Raffle> findByEventId(String eventId);
}
