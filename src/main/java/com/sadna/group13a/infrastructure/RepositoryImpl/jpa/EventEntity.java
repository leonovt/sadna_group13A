package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "events")
public class EventEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String companyId;

    @Column
    private String category;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(nullable = false)
    private String data;

    protected EventEntity() {
        // required by Hibernate
    }

    public EventEntity(String id, String companyId, String category, boolean published, String title, String data) {
        this.id = id;
        this.companyId = companyId;
        this.category = category;
        this.published = published;
        this.title = title;
        this.data = data;
    }

    public String getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getCategory() { return category; }
    public boolean isPublished() { return published; }
    public String getTitle() { return title; }
    public String getData() { return data; }
}
