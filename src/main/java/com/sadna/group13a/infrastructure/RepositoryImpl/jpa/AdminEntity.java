package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "admins")
public class AdminEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Lob
    @Column(nullable = false)
    private String data;

    protected AdminEntity() {
        // required by Hibernate
    }

    public AdminEntity(String id, String userId, String data) {
        this.id = id;
        this.userId = userId;
        this.data = data;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getData() { return data; }
}
