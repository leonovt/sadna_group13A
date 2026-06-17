package com.sadna.group13a.domain.Aggregates.Inquiry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * An inquiry sent by a member to a production company (II.3.7); an owner of that company
 * may read and answer it (II.4.4). Identifying fields are immutable; the response is
 * recorded once answered.
 */
public class Inquiry {

    private final String id;
    private final String fromUserId;
    private final String companyId;
    private final String message;
    private final LocalDateTime createdAt;

    private InquiryStatus status;
    private String response;
    private String respondedByUserId;
    private LocalDateTime respondedAt;
    private volatile long version = 0L;

    @JsonCreator
    public Inquiry(@JsonProperty("id") String id,
                   @JsonProperty("fromUserId") String fromUserId,
                   @JsonProperty("companyId") String companyId,
                   @JsonProperty("message") String message,
                   @JsonProperty("createdAt") LocalDateTime createdAt) {
        this.id = id;
        this.fromUserId = fromUserId;
        this.companyId = companyId;
        this.message = message;
        this.createdAt = createdAt;
        this.status = InquiryStatus.OPEN;
    }

    /**
     * Records the company's answer and marks the inquiry answered.
     *
     * @throws IllegalStateException if the inquiry has already been answered
     * @throws IllegalArgumentException if the response is blank
     */
    public synchronized void answer(String responderUserId, String response) {
        if (status == InquiryStatus.ANSWERED) {
            throw new IllegalStateException("This inquiry has already been answered.");
        }
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("A response cannot be empty.");
        }
        this.response = response;
        this.respondedByUserId = responderUserId;
        this.respondedAt = LocalDateTime.now();
        this.status = InquiryStatus.ANSWERED;
        this.version++;
    }

    public String getId() { return id; }
    public String getFromUserId() { return fromUserId; }
    public String getCompanyId() { return companyId; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public InquiryStatus getStatus() { return status; }
    public String getResponse() { return response; }
    public String getRespondedByUserId() { return respondedByUserId; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public long getVersion() { return version; }
}
