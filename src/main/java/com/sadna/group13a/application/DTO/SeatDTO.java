package com.sadna.group13a.application.DTO;

import com.sadna.group13a.domain.Aggregates.Event.SeatStatus;

public class SeatDTO {
    private final String id;
    private final String label;
    private final SeatStatus status;

    public SeatDTO(String id, String label, SeatStatus status) {
        this.id = id;
        this.label = label;
        this.status = status;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public SeatStatus getStatus() { return status; }
}
