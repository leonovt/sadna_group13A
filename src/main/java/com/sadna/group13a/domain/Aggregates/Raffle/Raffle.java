package com.sadna.group13a.domain.Aggregates.Raffle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class Raffle
{
    private final String id;
    private final String eventId;
    private final String companyId;
    private RaffleStatus status;

    // Using a Set prevents the same user from registering twice!
    private final Set<String> participantUserIds;

    // Maps a winning User ID to their specific Authorization Code
    private final Map<String, AuthorizationCode> winners;

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
    public void registerParticipant(String userId) {
        if (this.status != RaffleStatus.OPEN_FOR_REGISTRATION) {
            throw new IllegalStateException("Cannot register: The raffle is no longer open.");
        }
        
        // The Set automatically ignores duplicates, but we can throw an error if we want to notify the frontend
        if (!participantUserIds.add(userId)) {
            throw new IllegalArgumentException("User is already registered for this raffle.");
        }
    }

    /**
     * Domain Logic: Executes the draw, selects winners, and generates their codes.
     * 
     * @param numberOfWinners How many people should win
     * @param codeValidMinutes How long the winners have to buy their tickets
     */
    public void executeDraw(int numberOfWinners, int codeValidMinutes) {
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
    }

    /**
     * Allows the application to check if a specific user won, and grab their code.
     */
    public Optional<AuthorizationCode> getAuthorizationCodeFor(String userId)
    {
        return Optional.ofNullable(winners.get(userId));
    }

    // --- Getters ---
    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public String getCompanyId() { return companyId; }
    public RaffleStatus getStatus() { return status; }
    
    // Return an unmodifiable view to protect the aggregate's internal state!
    public Set<String> getParticipantUserIds() { 
        return Collections.unmodifiableSet(participantUserIds); 
    }
    
    public Collection<AuthorizationCode> getWinningCodes() {
        return Collections.unmodifiableCollection(winners.values());
    }
}
