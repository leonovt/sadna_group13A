package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "complaints")
public class ComplaintEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String complainantUserId;

    @Lob
    @Column(nullable = false)
    private String data;

    protected ComplaintEntity() {
        // required by Hibernate
    }

    public ComplaintEntity(String id, String complainantUserId, String data) {
        this.id = id;
        this.complainantUserId = complainantUserId;
        this.data = data;
    }

    public String getId() { return id; }
    public String getComplainantUserId() { return complainantUserId; }
    public String getData() { return data; }
}
