package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_notifications")
public class UserNotificationEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Lob
    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private String type;

    @Column
    private String metadata;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected UserNotificationEntity() {
        // required by Hibernate
    }

    public UserNotificationEntity(String id, String userId, String message,
                                   String type, String metadata, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.message = message;
        this.type = type;
        this.metadata = metadata;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getMessage() { return message; }
    public String getType() { return type; }
    public String getMetadata() { return metadata; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
