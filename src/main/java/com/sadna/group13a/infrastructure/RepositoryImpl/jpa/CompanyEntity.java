package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "companies")
public class CompanyEntity {

    @Id
    private String id;

    @Lob
    @Column(nullable = false)
    private String data;

    protected CompanyEntity() {
        // required by Hibernate
    }

    public CompanyEntity(String id, String data) {
        this.id = id;
        this.data = data;
    }

    public String getId() { return id; }
    public String getData() { return data; }
}
