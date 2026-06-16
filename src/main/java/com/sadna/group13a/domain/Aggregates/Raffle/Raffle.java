package com.sadna.group13a.domain.Aggregates.Raffle;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(name = "raffles")
public class Raffle
{
    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RaffleStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private volatile long version = 0L;

    // Using a Set prevents the same user from registering twice!
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "raffle_participants", joinColumns = @JoinColumn(name = "raffle_id"))
    @Column(name = "user_id")
    private Set<String> participantUserIds;

    // Maps a winning User ID to their specific Authorization Code
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "raffle_id")
    @MapKey(name = "userId")
    private Map<String, AuthorizationCode> winners;

    /** Required by JPA. Do not use in business code. */
    protected Raffle() {}

    public Raffle(String id, String eventId, String companyId)
    {
        this.id = id;
        this.eventId = eventId;
        this.companyId = companyId;
        this.status = RaffleStatus.OPEN_FOR_REGISTRATION;
        this.participantUserIds = new HashSet<>();
        this.winners = new HashMap<>();
    }

    /**
     * Domain Logic: Register a participant.
     * Note: The Application Service MUST verify the user is a registered Member
     * before passing their ID to this method[cite: 1].
     */
    public synchronized void registerParticipant(String userId) {
        if (this.status != RaffleStatus.OPEN_FOR_REGISTRATION) {
            throw new IllegalStateException("Cannot register: The raffle is no longer open.");
        }

        // The Set automatically ignores duplicates, but we can throw an error if we want to notify the frontend
        if (!participantUserIds.add(userId)) {
            throw new IllegalArgumentException("User is already registered for this raffle.");
        }
        version++;
    }

    /**
     * Domain Logic: Executes the draw, selects winners, and generates their codes.
     *
     * @param numberOfWinners How many people should win
     * @param codeValidMinutes How long the winners have to buy their tickets
     */
    public synchronized void executeDraw(int numberOfWinners, int codeValidMinutes) {
        if (this.status != RaffleStatus.OPEN_FOR_REGISTRATION) {
            throw new IllegalStateException("Cannot draw: Raffle is not in the correct state.");
        }

        // 1. Convert Set to List and Shuffle it for randomness
        List<String> shuffledParticipants = new ArrayList<>(participantUserIds);
        Collections.shuffle(shuffledParticipants);

        // 2. Figure out how many winners we actually have (in case there are fewer participants than tickets)
        int actualWinnersCount = Math.min(numberOfWinners, shuffledParticipants.size());

        // 3. Pick the winners and generate a time-limited authorization code for each[cite: 1]
        for (int i = 0; i < actualWinnersCount; i++) {
            String winningUserId = shuffledParticipants.get(i);
            AuthorizationCode code = new AuthorizationCode(winningUserId, this.eventId, codeValidMinutes);
            winners.put(winningUserId, code);
        }

        // 4. Update the state to prevent future registrations or re-draws
        this.status = RaffleStatus.DRAWN;
        version++;
    }

    /**
     * Allows the application to check if a specific user won, and grab their code.
     */
    public Optional<AuthorizationCode> getAuthorizationCodeFor(String userId)
    {
        return Optional.ofNullable(winners.get(userId));
    }

    /**
     * Closes the raffle permanently (e.g. event cancelled or owner decision).
     */
    public synchronized void close() {
        this.status = RaffleStatus.CLOSED;
        version++;
    }

    // --- Getters ---
    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public String getCompanyId() { return companyId; }
    public RaffleStatus getStatus() { return status; }
    public long getVersion() { return version; }

    // Return an unmodifiable view to protect the aggregate's internal state!
    public Set<String> getParticipantUserIds() {
        return Collections.unmodifiableSet(participantUserIds);
    }

    public Collection<AuthorizationCode> getWinningCodes() {
        return Collections.unmodifiableCollection(winners.values());
    }
}
