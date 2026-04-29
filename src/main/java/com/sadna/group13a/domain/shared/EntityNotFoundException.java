package com.sadna.group13a.domain.shared;

/**
 * Thrown when a referenced entity (User, Event, Order, etc.)
 * is not found in its repository.
 */
public class EntityNotFoundException extends DomainException {

    public EntityNotFoundException(String entityType, String id) {
        super(entityType + " not found with id: " + id);
    }
}
