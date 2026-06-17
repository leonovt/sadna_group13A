package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String username;

    @Lob
    @Column(nullable = false)
    private String data;

    protected UserEntity() {
        // required by Hibernate
    }

    public UserEntity(String id, String username, String data) {
        this.id = id;
        this.username = username;
        this.data = data;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getData() { return data; }
}
