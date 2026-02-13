package com.orasa.backend.scheduler;

import org.springframework.stereotype.Component;

import com.orasa.backend.service.sms.SmsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmsReminderScheduler {

    private final SmsService smsService;

    // @Scheduled(fixedRate = 300000)
    public void processPendingReminders() {
        log.debug("Running SMS reminder scheduler...");
        try {
            smsService.processPendingReminders();
        } catch (Exception e) {
            log.error("Error in SMS reminder scheduler: {}", e.getMessage(), e);
        }
    }
}
