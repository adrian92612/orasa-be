package com.orasa.backend.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID businessId;

    @Column(nullable = false)
    private String merchantOrderNo;

    private String platOrderNo;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String paymentLink;

    @Column(columnDefinition = "TEXT")
    private String paymentImage;

    private String errorMessage;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public enum PaymentStatus {
        PENDING,
        SUCCESS,
        FAILED,
        EXPIRED
    }

    public enum PaymentType {
        SUBSCRIPTION_RENEWAL,
        CREDIT_TOPUP
    }
}
