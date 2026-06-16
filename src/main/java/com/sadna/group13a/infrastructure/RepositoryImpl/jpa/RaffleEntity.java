package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "raffles")
public class RaffleEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String companyId;

    @Lob
    @Column(nullable = false)
    private String data;

    protected RaffleEntity() {
        // required by Hibernate
    }

    public RaffleEntity(String id, String eventId, String companyId, String data) {
        this.id = id;
        this.eventId = eventId;
        this.companyId = companyId;
        this.data = data;
    }

    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public String getCompanyId() { return companyId; }
    public String getData() { return data; }
}
