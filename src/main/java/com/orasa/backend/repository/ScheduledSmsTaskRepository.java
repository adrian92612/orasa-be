package com.orasa.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.orasa.backend.domain.ScheduledSmsTaskEntity;

@Repository
public interface ScheduledSmsTaskRepository extends JpaRepository<ScheduledSmsTaskEntity, UUID> {

    @Query("SELECT t FROM ScheduledSmsTaskEntity t WHERE t.status = 'PENDING' AND t.scheduledAt < :cutoff")
    List<ScheduledSmsTaskEntity> findOverduePendingTasks(@Param("cutoff") OffsetDateTime cutoff);
}
