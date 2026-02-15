package com.orasa.backend.worker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.orasa.backend.dto.sms.SmsReminderTask;
import com.orasa.backend.service.sms.SmsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmsReminderWorker {

    private final RedissonClient redissonClient;
    private final SmsService smsService;

    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int poolSize;

    // We keep one thread dedicated to polling the queue
    private final ExecutorService pollerExecutor = Executors.newSingleThreadExecutor();
    
    // We use a thread pool for actually processing the tasks concurrently
    private ExecutorService workerExecutor;

    private RBlockingQueue<SmsReminderTask> blockingQueue;
    
    private volatile boolean isRunning = true;

    @PostConstruct
    public void start() {
        this.blockingQueue = redissonClient.getBlockingQueue("smsRemindersQueue");
        
        this.workerExecutor = Executors.newFixedThreadPool(poolSize);
        log.info("SmsReminderWorker initialized with pool size {}", poolSize);

        pollerExecutor.submit(() -> {
            log.info("SmsReminderWorker started, listening for tasks...");
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    // This blocks until a task is available (respecting the delay set during add)
                    SmsReminderTask task = blockingQueue.take();
                    
                    // Submit to worker pool for processing
                    workerExecutor.submit(() -> processTask(task));
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("SmsReminderWorker polling interrupted");
                } catch (Exception e) {
                    log.error("Error retrieving SMS reminder task", e);
                    // Add a small sleep to prevent tight loop on persistent errors
                    try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        });
    }

    private void processTask(SmsReminderTask task) {
        log.info("Processing SMS reminder task for appointment {}", task.getAppointmentId());
        try {
            smsService.processScheduledTask(task);
        } catch (Exception e) {
            log.error("Failed to process task for appointment {}", task.getAppointmentId(), e);
        }
    }

    @PreDestroy
    public void stop() {
        isRunning = false;
        pollerExecutor.shutdownNow();
        if (workerExecutor != null) {
            workerExecutor.shutdown();
            try {
                if (!workerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    workerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerExecutor.shutdownNow();
            }
        }
    }
}
