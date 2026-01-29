package com.orasa.backend.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    // Change this to nullable = false
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Ensure this matches the NOT NULL in SQL
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at") // This one stays nullable because it's only set on delete
    private OffsetDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
