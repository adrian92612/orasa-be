package com.orasa.backend.domain;

import com.orasa.backend.common.SmsTaskStatus; // You'll need this Enum
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "scheduled_sms_tasks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE scheduled_sms_tasks SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class ScheduledSmsTask extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SmsTaskStatus status = SmsTaskStatus.PENDING;
}