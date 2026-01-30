package com.orasa.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.util.UUID;

@Entity
@Table(name = "business_reminder_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE business_reminder_configs SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class BusinessReminderConfig extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "lead_time_hours", nullable = false)
    private Integer leadTimeHours;

    @Column(name = "message_template", columnDefinition = "TEXT")
    private String messageTemplate;

    @Column(name = "is_enabled")
    private boolean isEnabled = true;
}