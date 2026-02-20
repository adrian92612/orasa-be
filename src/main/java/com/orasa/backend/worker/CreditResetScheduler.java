package com.orasa.backend.worker;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.dto.CreditResetTask;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.service.SubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditResetScheduler {

    private final RedissonClient redissonClient;
    private final BusinessRepository businessRepository;
    private final SubscriptionService subscriptionService;
    private final Clock clock;

    private final ExecutorService pollerExecutor = Executors.newSingleThreadExecutor();
    private RBlockingQueue<CreditResetTask> blockingQueue;
    private volatile boolean isRunning = true;

    @PostConstruct
    public void start() {
        this.blockingQueue = redissonClient.getBlockingQueue("creditResetQueue");

        pollerExecutor.submit(() -> {
            log.info("CreditResetScheduler started, listening for tasks...");
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    CreditResetTask task = blockingQueue.take();
                    processTask(task);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("CreditResetScheduler polling interrupted");
                } catch (Exception e) {
                    log.error("Error retrieving credit reset task", e);
                    try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        });
    }

    private void processTask(CreditResetTask task) {
        log.info("Processing credit reset for business {}", task.getBusinessId());
        try {
            BusinessEntity business = businessRepository.findById(task.getBusinessId()).orElse(null);
            if (business == null) {
                log.warn("Business {} not found for credit reset", task.getBusinessId());
                return;
            }
            subscriptionService.checkAndRefreshCredits(business);
        } catch (Exception e) {
            log.error("Failed to reset credits for business {}", task.getBusinessId(), e);
        }
    }

    @Scheduled(fixedRate = 300_000)
    public void recoverOverdueResets() {
        List<BusinessEntity> dueBusinesses = businessRepository
                .findBusinessesDueForCreditReset(OffsetDateTime.now(clock));

        if (dueBusinesses.isEmpty()) {
            return;
        }

        log.info("Credit reset recovery found {} businesses due for reset", dueBusinesses.size());

        for (BusinessEntity business : dueBusinesses) {
            try {
                subscriptionService.checkAndRefreshCredits(business);
                log.info("Recovery: reset credits for business {}", business.getId());
            } catch (Exception e) {
                log.error("Recovery: failed to reset credits for business {}", business.getId(), e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        isRunning = false;
        pollerExecutor.shutdownNow();
    }
}
