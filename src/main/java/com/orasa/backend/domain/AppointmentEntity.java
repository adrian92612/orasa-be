package com.orasa.backend.domain;

import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.AppointmentType;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE appointments SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class AppointmentEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private BusinessEntity business;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AppointmentType type = AppointmentType.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private BranchEntity branch;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(name = "start_date_time", nullable = false)
    private OffsetDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private OffsetDateTime endDateTime;

    @Column(name = "reminders_enabled", nullable = false)
    @Builder.Default
    private boolean remindersEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "additional_reminder_minutes")
    private Integer additionalReminderMinutes;

    @Column(name = "additional_reminder_template", columnDefinition = "TEXT")
    private String additionalReminderTemplate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_id")
    @NotFound(action = NotFoundAction.IGNORE)
    private ServiceEntity service;

    @Column(name = "service_id", insertable = false, updatable = false)
    private UUID rawServiceId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "appointment_reminders",
        joinColumns = @JoinColumn(name = "appointment_id"),
        inverseJoinColumns = @JoinColumn(name = "reminder_config_id")
    )
    @BatchSize(size = 50)
    private Set<BusinessReminderConfigEntity> selectedReminders;
}