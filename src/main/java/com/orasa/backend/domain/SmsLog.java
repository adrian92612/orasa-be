package com.orasa.backend.domain;

import com.orasa.backend.common.SmsStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sms_logs")
@Getter
@Setter
public class SmsLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

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
