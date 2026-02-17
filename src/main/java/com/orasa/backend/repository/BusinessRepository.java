package com.orasa.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orasa.backend.common.SubscriptionStatus;
import com.orasa.backend.domain.BusinessEntity;

public interface BusinessRepository extends JpaRepository<BusinessEntity, UUID> {
  Optional<BusinessEntity> findBySlug(String slug);
  Page<BusinessEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
  Page<BusinessEntity> findBySubscriptionStatus(SubscriptionStatus status, Pageable pageable);
  Page<BusinessEntity> findByNameContainingIgnoreCaseAndSubscriptionStatus(String name, SubscriptionStatus status, Pageable pageable);

  @Query("SELECT b FROM BusinessEntity b WHERE b.subscriptionStatus = 'ACTIVE' AND b.nextCreditResetDate IS NOT NULL AND b.nextCreditResetDate <= :now")
  List<BusinessEntity> findBusinessesDueForCreditReset(@Param("now") OffsetDateTime now);
}

