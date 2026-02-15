package com.orasa.backend.repository;

import com.orasa.backend.domain.ScheduledSmsTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ScheduledSmsTaskRepository extends JpaRepository<ScheduledSmsTaskEntity, UUID> {
}
