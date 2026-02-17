package com.orasa.backend.service;

import java.time.Clock;
import java.time.OffsetDateTime;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.SubscriptionStatus;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.exception.SubscriptionExpiredException;
import com.orasa.backend.repository.BusinessRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SubscriptionService {

    private final BusinessRepository businessRepository;
    private final Clock clock;

    @Transactional
    public boolean isSubscriptionActive(UUID businessId) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        return isSubscriptionActive(business);
    }

    @Transactional
    public boolean isSubscriptionActive(BusinessEntity business) {
        if (business.getSubscriptionStatus() == SubscriptionStatus.ACTIVE 
                && business.getSubscriptionEndDate() != null
                && business.getSubscriptionEndDate().isBefore(OffsetDateTime.now(clock))) {
            
            log.info("Business {} subscription expired on {}", 
                    business.getId(), business.getSubscriptionEndDate());
            
            business.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            businessRepository.save(business);
        } else if (business.getSubscriptionStatus() != SubscriptionStatus.ACTIVE 
                && business.getSubscriptionEndDate() != null 
                && business.getSubscriptionEndDate().isAfter(OffsetDateTime.now(clock))) {
            
            log.info("Business {} subscription auto-reactivated. End date: {}", 
                    business.getId(), business.getSubscriptionEndDate());
            
            business.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
            businessRepository.save(business);
        }

        return business.getSubscriptionStatus() == SubscriptionStatus.ACTIVE;
    }

    public void validateActiveSubscription(UUID businessId) {
        validateActiveSubscription(businessId, false);
    }

    public void validateActiveSubscription(UUID businessId, boolean allowPending) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        validateActiveSubscription(business, allowPending);
    }

    public void validateActiveSubscription(BusinessEntity business) {
        validateActiveSubscription(business, false);
    }

    public void validateActiveSubscription(BusinessEntity business, boolean allowPending) {
        if (allowPending && business.getSubscriptionStatus() == SubscriptionStatus.PENDING) {
            log.debug("Bypassing subscription check for PENDING business {} (allowPending=true)", business.getId());
            return;
        }

        if (!isSubscriptionActive(business)) {
            String status = business.getSubscriptionStatus().name();
            String message = switch (business.getSubscriptionStatus()) {
                case PENDING -> "Subscription is pending activation. Please complete payment.";
                case EXPIRED -> "Your subscription has expired. Please renew to continue.";
                case CANCELLED -> "Your subscription has been cancelled.";
                default -> "Subscription is not active.";
            };
            
            throw new SubscriptionExpiredException(message, status);
        }
    }

    public SubscriptionInfo getSubscriptionInfo(UUID businessId) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        return new SubscriptionInfo(
                business.getSubscriptionStatus(),
                business.getSubscriptionStartDate(),
                business.getSubscriptionEndDate(),
                isSubscriptionActive(business)
        );
    }

    public record SubscriptionInfo(
            SubscriptionStatus status,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            boolean isActive
    ) {}

    @Transactional
    public void activateSubscription(UUID businessId) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        business.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        business.setSubscriptionStartDate(OffsetDateTime.now(clock));
        
        // Default 1 month if not set
        if (business.getSubscriptionEndDate() == null || business.getSubscriptionEndDate().isBefore(OffsetDateTime.now(clock))) {
            business.setSubscriptionEndDate(OffsetDateTime.now(clock).plusMonths(1));
        }

        // Reset SMS credits from start
        business.setFreeSmsCredits(100); 
        
        // Set next credit reset date
        business.setNextCreditResetDate(OffsetDateTime.now(clock).plusMonths(1));
        
        businessRepository.save(business);
        log.info("Activated subscription for business {}", businessId);
    }

    @Transactional
    public void cancelSubscription(UUID businessId) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        business.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
        business.setSubscriptionEndDate(OffsetDateTime.now(clock));
        business.setNextCreditResetDate(null);
        
        businessRepository.save(business);
        log.info("Cancelled subscription for business {}", businessId);
    }

    @Transactional
    public void extendSubscription(UUID businessId, int months) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        if (business.getSubscriptionStatus() != SubscriptionStatus.ACTIVE) {
            activateSubscription(businessId);
            if (months > 1) {
                business.setSubscriptionEndDate(business.getSubscriptionEndDate().plusMonths(months - 1));
                businessRepository.save(business);
            }
            return;
        }
        
        OffsetDateTime baseDate = (business.getSubscriptionEndDate() != null && business.getSubscriptionEndDate().isAfter(OffsetDateTime.now(clock)))
                ? business.getSubscriptionEndDate()
                : OffsetDateTime.now(clock);
        
        business.setSubscriptionEndDate(baseDate.plusMonths(months));
        businessRepository.save(business);
        log.info("Extended subscription for business {} by {} months. New end date: {}", 
                businessId, months, business.getSubscriptionEndDate());
    }

    @Transactional
    public void consumeSmsCredit(UUID businessId) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        
        consumeSmsCredit(business);
    }

    @Transactional(noRollbackFor = SubscriptionExpiredException.class)
    public void consumeSmsCredit(BusinessEntity business) {
        // 1. Lazy Reset: Check if we moved into a new cycle since last check
        checkAndRefreshCredits(business);

        // 2. Consume
        if (business.getFreeSmsCredits() > 0) {
            business.setFreeSmsCredits(business.getFreeSmsCredits() - 1);
        } else if (business.getPaidSmsCredits() > 0) {
            business.setPaidSmsCredits(business.getPaidSmsCredits() - 1);
        } else {
            throw new SubscriptionExpiredException("Insufficient SMS credits", "ACTIVE_NO_CREDITS");
        }
        
        businessRepository.save(business);
    }

    @Transactional
    public void checkAndRefreshCredits(BusinessEntity business) {
        // 1. Check for Expiry First
        if (handleExpiryCheck(business)) {
            return; // If expired, stop processing
        }

        if (business.getSubscriptionStatus() != SubscriptionStatus.ACTIVE) {
            return;
        }

        // If next resetting date has passed, trigger the reset
        if (business.getNextCreditResetDate() != null && !business.getNextCreditResetDate().isAfter(OffsetDateTime.now(clock))) {
            log.info("Lazy-refreshing credits for business {}", business.getId());
            
            business.setFreeSmsCredits(100);
            
            // Advance reset date by 1 month
            // Ensure we don't fall behind if multiple months passed (though rare if system is active)
            while (!business.getNextCreditResetDate().isAfter(OffsetDateTime.now(clock))) {
                 business.setNextCreditResetDate(business.getNextCreditResetDate().plusMonths(1));
            }
            
            businessRepository.save(business);
        }
    }

    private boolean handleExpiryCheck(BusinessEntity business) {
        if (business.getSubscriptionStatus() == SubscriptionStatus.ACTIVE 
            && business.getSubscriptionEndDate() != null 
            && business.getSubscriptionEndDate().isBefore(OffsetDateTime.now(clock))) {
            
            log.info("Lazy-expiring subscription for business {}", business.getId());
            business.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            business.setFreeSmsCredits(0);
            businessRepository.save(business);
            return true;
        }
        return false;
        
    }
    @Transactional
    public void addPaidCredits(UUID businessId, int credits) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        business.setPaidSmsCredits(business.getPaidSmsCredits() + credits);
        businessRepository.save(business);
    }
}
