package com.orasa.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.PaymentEntity;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    java.util.List<PaymentEntity> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    Optional<PaymentEntity> findByPlatOrderNo(String platOrderNo);
    Optional<PaymentEntity> findByMerchantOrderNo(String merchantOrderNo);
}
