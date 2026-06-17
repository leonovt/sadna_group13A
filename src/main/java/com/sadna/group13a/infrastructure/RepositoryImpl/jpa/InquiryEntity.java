package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "inquiries")
public class InquiryEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String companyId;

    @Column(nullable = false)
    private String fromUserId;

    @Lob
    @Column(nullable = false)
    private String data;

    protected InquiryEntity() {
        // required by Hibernate
    }

    public InquiryEntity(String id, String companyId, String fromUserId, String data) {
        this.id = id;
        this.companyId = companyId;
        this.fromUserId = fromUserId;
        this.data = data;
    }

    public String getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getFromUserId() { return fromUserId; }
    public String getData() { return data; }
}
