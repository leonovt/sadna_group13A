package com.sadna.group13a.domain.Aggregates.Complaint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * A complaint submitted by a member to the platform administrators (II.3.3), who may
 * respond and resolve it (II.6.3). The aggregate is immutable in its identifying fields
 * and tracks the admin's response and resolution.
 */
public class Complaint {

    private final String id;
    private final String complainantUserId;
    private final String subject;
    private final String message;
    private final LocalDateTime createdAt;

    private ComplaintStatus status;
    private String adminResponse;
    private String resolvedByAdminId;
    private LocalDateTime resolvedAt;
    private volatile long version = 0L;

    @JsonCreator
    public Complaint(@JsonProperty("id") String id,
                     @JsonProperty("complainantUserId") String complainantUserId,
                     @JsonProperty("subject") String subject,
                     @JsonProperty("message") String message,
                     @JsonProperty("createdAt") LocalDateTime createdAt) {
        this.id = id;
        this.complainantUserId = complainantUserId;
        this.subject = subject;
        this.message = message;
        this.createdAt = createdAt;
        this.status = ComplaintStatus.OPEN;
    }

    /**
     * Records an administrator's response and marks the complaint resolved.
     *
     * @throws IllegalStateException if the complaint has already been resolved
     * @throws IllegalArgumentException if the response is blank
     */
    public synchronized void respond(String adminId, String response) {
        if (status == ComplaintStatus.RESOLVED) {
            throw new IllegalStateException("This complaint has already been resolved.");
        }
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("A response cannot be empty.");
        }
        this.adminResponse = response;
        this.resolvedByAdminId = adminId;
        this.resolvedAt = LocalDateTime.now();
        this.status = ComplaintStatus.RESOLVED;
        this.version++;
    }

    public String getId() { return id; }
    public String getComplainantUserId() { return complainantUserId; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public ComplaintStatus getStatus() { return status; }
    public String getAdminResponse() { return adminResponse; }
    public String getResolvedByAdminId() { return resolvedByAdminId; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public long getVersion() { return version; }
}
