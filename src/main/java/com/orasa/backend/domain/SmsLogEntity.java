package com.orasa.backend.domain;

import com.orasa.backend.common.SmsStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "sms_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE sms_logs SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class SmsLogEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private BusinessEntity business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private AppointmentEntity appointment;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(columnDefinition = "TEXT")
    private String messageBody;

    @Enumerated(EnumType.STRING)
    private SmsStatus status;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "error_message")
    private String errorMessage;
}
