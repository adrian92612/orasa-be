package com.orasa.backend.domain;

import com.orasa.backend.common.AppointmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity
@Table(name = "appointments")
@Getter
@Setter
public class Appointment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private User staff;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(name = "appointment_time", nullable = false)
    private OffsetDateTime appointmentTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Column(columnDefinition = "TEXT")
    private String notes;
}