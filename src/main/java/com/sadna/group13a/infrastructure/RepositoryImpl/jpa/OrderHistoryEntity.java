package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_histories")
public class OrderHistoryEntity {

    @Id
    private String receiptId;

    @Column(nullable = false)
    private String userId;

    @Lob
    @Column(nullable = false)
    private String data;

    protected OrderHistoryEntity() {
        // required by Hibernate
    }

    public OrderHistoryEntity(String receiptId, String userId, String data) {
        this.receiptId = receiptId;
        this.userId = userId;
        this.data = data;
    }

    public String getReceiptId() { return receiptId; }
    public String getUserId() { return userId; }
    public String getData() { return data; }
}
