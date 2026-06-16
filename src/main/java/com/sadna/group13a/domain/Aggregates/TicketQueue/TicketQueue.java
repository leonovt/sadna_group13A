package com.sadna.group13a.domain.Aggregates.TicketQueue;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Aggregate Root for the virtual waiting queue of a single event.
 * Controls how many users can access the ticket-selection screen concurrently.
 * Pure domain logic — no framework dependencies.
 */
@Entity
@Table(name = "ticket_queues")
public class TicketQueue {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "max_concurrent_users", nullable = false)
    private int maxConcurrentUsers;

    @Version
    @Column(name = "version", nullable = false)
    private volatile long version = 0L;

    // userId -> granted ticket (with expiry). Expired entries are lazily evicted.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "active_ticket_queue_id")
    @MapKey(name = "userId")
    private Map<String, QueueTicket> activeUsers;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "waiting_ticket_queue_id")
    @OrderBy("positionInLine ASC")
    private List<QueueTicket> waitingUsers;

    /** Required by JPA. Do not use in business code. */
    protected TicketQueue() {}

    public TicketQueue(String eventId, int maxConcurrentUsers) {
        if (maxConcurrentUsers < 1) throw new IllegalArgumentException("maxConcurrentUsers must be >= 1");
        this.eventId = eventId;
        this.maxConcurrentUsers = maxConcurrentUsers;
        this.activeUsers = new ConcurrentHashMap<>();
        this.waitingUsers = new CopyOnWriteArrayList<>();
    }

    // ── Commands ──────────────────────────────────────────────────

    /**
     * Adds a user to the end of the waiting list.
     * Throws if they are already active or already waiting.
     */
    public void joinQueue(String userId) {
        evictExpiredActiveUsers();

        if (activeUsers.containsKey(userId)) {
            throw new IllegalArgumentException("User is already active and allowed to purchase.");
        }
        if (waitingUsers.stream().anyMatch(t -> t.getUserId().equals(userId))) {
            throw new IllegalArgumentException("User is already in the waiting queue.");
        }

        int newPosition = waitingUsers.size() + 1;
        waitingUsers.add(new QueueTicket(userId, newPosition));
        version++;
    }

    /**
     * Admits up to batchSize users from the front of the waiting list, respecting maxConcurrentUsers.
     * Returns the list of newly admitted tickets so their expiry times can be communicated.
     */
    public List<QueueTicket> processBatch(int batchSize, int validMinutes) {
        evictExpiredActiveUsers();

        int availableSlots = maxConcurrentUsers - activeUsers.size();
        int toProcess = Math.min(Math.min(batchSize, availableSlots), waitingUsers.size());

        List<QueueTicket> granted = new ArrayList<>();
        for (int i = 0; i < toProcess; i++) {
            QueueTicket ticket;
            try {
                ticket = waitingUsers.remove(0);
            } catch (IndexOutOfBoundsException e) {
                // Another thread removed the last waiter between size-check and remove; stop early
                break;
            }
            ticket.grantAccess(validMinutes);
            activeUsers.put(ticket.getUserId(), ticket);
            granted.add(ticket);
        }

        int admitted = granted.size();
        for (QueueTicket t : waitingUsers) {
            t.decrementPosition(admitted);
        }

        if (admitted > 0) version++;
        return granted;
    }

    /**
     * Removes a user from the active set (e.g. they finished or abandoned checkout).
     */
    public void removeActiveUser(String userId) {
        activeUsers.remove(userId);
        version++;
    }

    /**
     * Removes a user from the waiting list (e.g. they left before being admitted).
     * Decrements positions of remaining waiters behind the removed user.
     * Returns true if the user was found and removed.
     */
    public boolean removeWaitingUser(String userId) {
        Optional<QueueTicket> toRemove = waitingUsers.stream()
                .filter(t -> t.getUserId().equals(userId))
                .findFirst();
        if (toRemove.isEmpty()) return false;

        int removedPosition = toRemove.get().getPositionInLine();
        waitingUsers.remove(toRemove.get());
        for (QueueTicket t : waitingUsers) {
            if (t.getPositionInLine() > removedPosition) {
                t.decrementPosition(1);
            }
        }
        version++;
        return true;
    }

    /**
     * Admin operation: clears all waiting and active users.
     */
    public void clearQueue() {
        waitingUsers.clear();
        activeUsers.clear();
        version++;
    }

    /**
     * Admin operation: adjusts the maximum concurrent user limit.
     */
    public void adjustMaxConcurrentUsers(int newMax) {
        if (newMax < 1) throw new IllegalArgumentException("Max concurrent users must be at least 1.");
        this.maxConcurrentUsers = newMax;
        version++;
    }

    // ── Queries ───────────────────────────────────────────────────

    public boolean isUserActive(String userId) {
        QueueTicket ticket = activeUsers.get(userId);
        if (ticket == null) return false;
        if (!ticket.hasAccess()) {
            activeUsers.remove(userId);
            return false;
        }
        return true;
    }

    /** Returns the active ticket for a user if they have valid access. */
    public Optional<QueueTicket> getActiveTicket(String userId) {
        QueueTicket ticket = activeUsers.get(userId);
        if (ticket == null) return Optional.empty();
        if (!ticket.hasAccess()) {
            activeUsers.remove(userId);
            return Optional.empty();
        }
        return Optional.of(ticket);
    }

    /** Returns the waiting ticket for a user if they are still in line. */
    public Optional<QueueTicket> getWaitingTicket(String userId) {
        return waitingUsers.stream()
                .filter(t -> t.getUserId().equals(userId))
                .findFirst();
    }

    public int getWaitingCount() { return waitingUsers.size(); }
    public int getActiveCount() { return activeUsers.size(); }
    public String getEventId() { return eventId; }
    public int getMaxConcurrentUsers() { return maxConcurrentUsers; }
    public long getVersion() { return version; }

    public Set<String> getActiveUserIds() {
        evictExpiredActiveUsers();
        return Collections.unmodifiableSet(activeUsers.keySet());
    }

    public List<QueueTicket> getWaitingUsers() {
        return new ArrayList<>(waitingUsers);
    }

    // ── Private helpers ───────────────────────────────────────────

    private void evictExpiredActiveUsers() {
        activeUsers.entrySet().removeIf(e -> !e.getValue().hasAccess());
    }
}
