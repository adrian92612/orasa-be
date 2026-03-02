package com.orasa.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;

import com.orasa.backend.common.CacheName;
import com.orasa.backend.common.SubscriptionStatus;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.dto.CreditResetTask;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.exception.SubscriptionExpiredException;
import com.orasa.backend.repository.BusinessRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SubscriptionServiceTest {

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private Clock clock;

    @Mock
    private CacheService cacheService;

    @Mock
    private RBlockingQueue<CreditResetTask> blockingQueue;

    @SuppressWarnings("deprecation")
    @Mock
    private RDelayedQueue<CreditResetTask> delayedQueue;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 3, 2, 10, 0, 0, 0, ZoneOffset.UTC);
    private UUID businessId;
    private BusinessEntity business;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        business = BusinessEntity.builder()
                .name("Test Business")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionStartDate(now.minusMonths(1))
                .subscriptionEndDate(now.plusMonths(1))
                .freeSmsCredits(100)
                .paidSmsCredits(0)
                .build();
        business.setId(businessId);

        when(clock.instant()).thenReturn(now.toInstant());
        when(clock.getZone()).thenReturn(now.getOffset());
    }

    // ─── Helper to set up Redis mocks ─────────────────────────────────────────

    private void mockRedisQueue() {
        doReturn(blockingQueue).when(redissonClient).getBlockingQueue("creditResetQueue");
        when(redissonClient.getDelayedQueue(blockingQueue)).thenReturn(delayedQueue);
    }

    // ─── isSubscriptionActive ─────────────────────────────────────────────────

    @Nested
    @DisplayName("isSubscriptionActive (by ID)")
    class IsSubscriptionActiveByIdTests {

        @Test
        @DisplayName("Should throw ResourceNotFoundException if business not found")
        void isSubscriptionActive_businessNotFound_throwsException() {
            when(businessRepository.findById(businessId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.isSubscriptionActive(businessId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Business not found");
        }

        @Test
        @DisplayName("Should return true for ACTIVE subscription that is not expired")
        void isSubscriptionActive_activeNotExpired_returnsTrue() {
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            boolean result = subscriptionService.isSubscriptionActive(businessId);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("isSubscriptionActive (by entity)")
    class IsSubscriptionActiveByEntityTests {

        @Test
        @DisplayName("Should auto-expire ACTIVE subscription with past end date")
        void isSubscriptionActive_activePastEndDate_expiresAndReturnsFalse() {
            business.setSubscriptionEndDate(now.minusDays(1));

            boolean result = subscriptionService.isSubscriptionActive(business);

            assertThat(result).isFalse();
            assertThat(business.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
            verify(businessRepository).save(business);
        }

        @Test
        @DisplayName("Should auto-reactivate non-ACTIVE subscription with future end date")
        void isSubscriptionActive_inactiveWithFutureEndDate_reactivatesAndReturnsTrue() {
            business.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            business.setSubscriptionEndDate(now.plusDays(5));
            // nextCreditResetDate in the past — should not schedule
            business.setNextCreditResetDate(now.minusDays(1));

            boolean result = subscriptionService.isSubscriptionActive(business);

            assertThat(result).isTrue();
            assertThat(business.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            verify(businessRepository).save(business);
        }

        @Test
        @DisplayName("Should schedule credit reset on reactivation if nextCreditResetDate is in the future")
        void isSubscriptionActive_reactivation_schedulesCreditResetWhenFutureResetDate() {
            mockRedisQueue();

            business.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            business.setSubscriptionEndDate(now.plusDays(5));
            business.setNextCreditResetDate(now.plusDays(15));

            subscriptionService.isSubscriptionActive(business);

            verify(delayedQueue).offer(any(CreditResetTask.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        @DisplayName("Should return false for PENDING status with no end date")
        void isSubscriptionActive_pendingNoEndDate_returnsFalse() {
            business.setSubscriptionStatus(SubscriptionStatus.PENDING);
            business.setSubscriptionEndDate(null);

            boolean result = subscriptionService.isSubscriptionActive(business);

            assertThat(result).isFalse();
            verify(businessRepository, never()).save(any());
        }
    }

    // ─── validateActiveSubscription ──────────────────────────────────────────

    @Nested
    @DisplayName("validateActiveSubscription")
    class ValidateActiveSubscriptionTests {

        @Test
        @DisplayName("Should pass for ACTIVE subscription")
        void validateActiveSubscription_activeSubscription_doesNotThrow() {
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            subscriptionService.validateActiveSubscription(businessId);
            // No exception = pass
        }

        @Test
        @DisplayName("Should throw SubscriptionExpiredException with PENDING message")
        void validateActiveSubscription_pendingStatus_throwsWithPendingMessage() {
            business.setSubscriptionStatus(SubscriptionStatus.PENDING);
            business.setSubscriptionEndDate(null);
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            assertThatThrownBy(() -> subscriptionService.validateActiveSubscription(businessId))
                    .isInstanceOf(SubscriptionExpiredException.class)
                    .hasMessageContaining("pending activation");
        }

        @Test
        @DisplayName("Should throw SubscriptionExpiredException with EXPIRED message")
        void validateActiveSubscription_expiredStatus_throwsWithExpiredMessage() {
            business.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            business.setSubscriptionEndDate(null);
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            assertThatThrownBy(() -> subscriptionService.validateActiveSubscription(businessId))
                    .isInstanceOf(SubscriptionExpiredException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Should throw SubscriptionExpiredException with CANCELLED message")
        void validateActiveSubscription_cancelledStatus_throwsWithCancelledMessage() {
            business.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
            business.setSubscriptionEndDate(null);
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            assertThatThrownBy(() -> subscriptionService.validateActiveSubscription(businessId))
                    .isInstanceOf(SubscriptionExpiredException.class)
                    .hasMessageContaining("cancelled");
        }

        @Test
        @DisplayName("Should bypass validation for PENDING status when allowPending=true")
        void validateActiveSubscription_pendingWithAllowPending_doesNotThrow() {
            business.setSubscriptionStatus(SubscriptionStatus.PENDING);

            // Call directly on entity (no repo needed)
            subscriptionService.validateActiveSubscription(business, true);
            // No exception = pass
        }
    }

    // ─── activateSubscription ────────────────────────────────────────────────

    @Nested
    @DisplayName("activateSubscription")
    class ActivateSubscriptionTests {

        @Test
        @DisplayName("Should activate subscription, reset credits, set dates, and schedule credit reset")
        void activateSubscription_success() {
            mockRedisQueue();
            business.setSubscriptionStatus(SubscriptionStatus.PENDING);
            business.setSubscriptionEndDate(null);
            business.setFreeSmsCredits(0);
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            subscriptionService.activateSubscription(businessId);

            assertThat(business.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(business.getFreeSmsCredits()).isEqualTo(100);
            assertThat(business.getSubscriptionEndDate()).isAfter(now);
            assertThat(business.getNextCreditResetDate()).isAfter(now);
            verify(businessRepository).save(business);
            verify(cacheService).evict(CacheName.BUSINESS, businessId);
            verify(delayedQueue).offer(any(CreditResetTask.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        @DisplayName("Should not reset end date if current end date is still in the future")
        void activateSubscription_existingFutureEndDate_keepsEndDate() {
            mockRedisQueue();
            OffsetDateTime existingEndDate = now.plusMonths(2);
            business.setSubscriptionEndDate(existingEndDate);
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            subscriptionService.activateSubscription(businessId);

            // End date should NOT be overwritten since it was already in the future
            assertThat(business.getSubscriptionEndDate()).isEqualTo(existingEndDate);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException if business not found")
        void activateSubscription_businessNotFound_throwsException() {
            when(businessRepository.findById(businessId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.activateSubscription(businessId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Business not found");
        }
    }

    // ─── cancelSubscription ──────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelSubscription")
    class CancelSubscriptionTests {

        @Test
        @DisplayName("Should set status to CANCELLED and clear nextCreditResetDate")
        void cancelSubscription_success() {
            business.setNextCreditResetDate(now.plusMonths(1));
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            subscriptionService.cancelSubscription(businessId);

            assertThat(business.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
            assertThat(business.getSubscriptionEndDate()).isNotNull();
            assertThat(business.getNextCreditResetDate()).isNull();
            verify(businessRepository).save(business);
            verify(cacheService).evict(CacheName.BUSINESS, businessId);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException if business not found")
        void cancelSubscription_businessNotFound_throwsException() {
            when(businessRepository.findById(businessId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.cancelSubscription(businessId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── extendSubscription ──────────────────────────────────────────────────

    @Nested
    @DisplayName("extendSubscription")
    class ExtendSubscriptionTests {

        @Test
        @DisplayName("Should extend end date from the current end date for ACTIVE subscription")
        void extendSubscription_activeFutureEndDate_extendsFromEndDate() {
            OffsetDateTime currentEndDate = now.plusDays(10);
            business.setSubscriptionEndDate(currentEndDate);
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            subscriptionService.extendSubscription(businessId, 2);

            assertThat(business.getSubscriptionEndDate()).isEqualTo(currentEndDate.plusMonths(2));
            verify(businessRepository).save(business);
            verify(cacheService).evict(CacheName.BUSINESS, businessId);
        }

        @Test
        @DisplayName("Should extend from now when ACTIVE subscription end date is in the past")
        void extendSubscription_activePastEndDate_extendsFromNow() {
            business.setSubscriptionEndDate(now.minusDays(1));
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            subscriptionService.extendSubscription(businessId, 1);

            assertThat(business.getSubscriptionEndDate()).isAfter(now);
        }

        @Test
        @DisplayName("Should activate and extend for non-ACTIVE business")
        void extendSubscription_inactiveBusiness_activatesFirst() {
            mockRedisQueue();
            business.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            business.setSubscriptionEndDate(null);
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            subscriptionService.extendSubscription(businessId, 1);

            // activateSubscription was called internally
            assertThat(business.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        }
    }

    // ─── consumeSmsCredit ────────────────────────────────────────────────────

    @Nested
    @DisplayName("consumeSmsCredit")
    class ConsumeSmsCreditsTests {

        @Test
        @DisplayName("Should consume one free credit when available")
        void consumeSmsCredit_hasFreeCredits_decrementsFreeSmsCredits() {
            business.setFreeSmsCredits(50);

            subscriptionService.consumeSmsCredit(business);

            assertThat(business.getFreeSmsCredits()).isEqualTo(49);
            assertThat(business.getPaidSmsCredits()).isEqualTo(0);
            verify(businessRepository).save(business);
        }

        @Test
        @DisplayName("Should consume one paid credit when free credits are exhausted")
        void consumeSmsCredit_noFreeCreditsHasPaid_decrementsPaidSmsCredits() {
            business.setFreeSmsCredits(0);
            business.setPaidSmsCredits(10);

            subscriptionService.consumeSmsCredit(business);

            assertThat(business.getFreeSmsCredits()).isEqualTo(0);
            assertThat(business.getPaidSmsCredits()).isEqualTo(9);
            verify(businessRepository).save(business);
        }

        @Test
        @DisplayName("Should throw SubscriptionExpiredException when no credits remain")
        void consumeSmsCredit_noCreditsAtAll_throwsException() {
            business.setFreeSmsCredits(0);
            business.setPaidSmsCredits(0);

            assertThatThrownBy(() -> subscriptionService.consumeSmsCredit(business))
                    .isInstanceOf(SubscriptionExpiredException.class)
                    .hasMessageContaining("Insufficient SMS credits");
        }

        @Test
        @DisplayName("Should lazily refresh credits before consuming if reset date passed")
        void consumeSmsCredit_resetDatePassed_refreshesCreditsFirstThenConsumes() {
            mockRedisQueue();
            business.setFreeSmsCredits(0);
            business.setPaidSmsCredits(0);
            // Set reset date in the past → triggers lazy refresh to 100 credits
            business.setNextCreditResetDate(now.minusDays(1));

            subscriptionService.consumeSmsCredit(business);

            // After refresh, free credits should be 99 (100 reset, 1 consumed)
            assertThat(business.getFreeSmsCredits()).isEqualTo(99);
        }
    }

    // ─── checkAndRefreshCredits ──────────────────────────────────────────────

    @Nested
    @DisplayName("checkAndRefreshCredits")
    class CheckAndRefreshCreditsTests {

        @Test
        @DisplayName("Should reset free credits and advance nextCreditResetDate when reset date has passed")
        void checkAndRefreshCredits_resetDatePassed_resetsCreditsAndAdvancesDate() {
            mockRedisQueue();
            business.setFreeSmsCredits(40);
            business.setNextCreditResetDate(now.minusDays(1));

            subscriptionService.checkAndRefreshCredits(business);

            assertThat(business.getFreeSmsCredits()).isEqualTo(100);
            assertThat(business.getNextCreditResetDate()).isAfter(now);
            verify(businessRepository).save(business);
            verify(cacheService).evict(CacheName.BUSINESS, businessId);
        }

        @Test
        @DisplayName("Should do nothing when reset date is still in the future")
        void checkAndRefreshCredits_resetDateNotPassed_doesNothing() {
            business.setFreeSmsCredits(75);
            business.setNextCreditResetDate(now.plusDays(15));

            subscriptionService.checkAndRefreshCredits(business);

            assertThat(business.getFreeSmsCredits()).isEqualTo(75);
            verify(businessRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should lazy-expire subscription and skip credit reset if subscription has ended")
        void checkAndRefreshCredits_subscriptionExpired_lazyExpiresAndSkipsReset() {
            business.setSubscriptionEndDate(now.minusDays(1));
            business.setNextCreditResetDate(now.minusDays(1));

            subscriptionService.checkAndRefreshCredits(business);

            assertThat(business.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
            assertThat(business.getFreeSmsCredits()).isEqualTo(0);
            verify(businessRepository).save(business);
        }

        @Test
        @DisplayName("Should do nothing for non-ACTIVE status with no expiry to handle")
        void checkAndRefreshCredits_pendingStatus_doesNothing() {
            business.setSubscriptionStatus(SubscriptionStatus.PENDING);
            business.setSubscriptionEndDate(null);
            business.setNextCreditResetDate(now.minusDays(1)); // wouldn't matter

            subscriptionService.checkAndRefreshCredits(business);

            assertThat(business.getFreeSmsCredits()).isEqualTo(100); // unchanged builder default
            verify(businessRepository, never()).save(any());
        }
    }

    // ─── addPaidCredits ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("addPaidCredits")
    class AddPaidCreditsTests {

        @Test
        @DisplayName("Should add paid credits to existing balance")
        void addPaidCredits_success() {
            business.setPaidSmsCredits(5);
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            subscriptionService.addPaidCredits(businessId, 50);

            assertThat(business.getPaidSmsCredits()).isEqualTo(55);
            verify(businessRepository).save(business);
            verify(cacheService).evict(CacheName.BUSINESS, businessId);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException if business not found")
        void addPaidCredits_businessNotFound_throwsException() {
            when(businessRepository.findById(businessId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.addPaidCredits(businessId, 50))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Business not found");
        }
    }

    // ─── getSubscriptionInfo ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getSubscriptionInfo")
    class GetSubscriptionInfoTests {

        @Test
        @DisplayName("Should return correct SubscriptionInfo for an active business")
        void getSubscriptionInfo_activeBusiness_returnsCorrectInfo() {
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            SubscriptionService.SubscriptionInfo info = subscriptionService.getSubscriptionInfo(businessId);

            assertThat(info.status()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(info.isActive()).isTrue();
            assertThat(info.endDate()).isEqualTo(business.getSubscriptionEndDate());
            assertThat(info.startDate()).isEqualTo(business.getSubscriptionStartDate());
        }

        @Test
        @DisplayName("Should return isActive=false for expired business")
        void getSubscriptionInfo_expiredBusiness_returnsInactiveInfo() {
            business.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            business.setSubscriptionEndDate(null);
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

            SubscriptionService.SubscriptionInfo info = subscriptionService.getSubscriptionInfo(businessId);

            assertThat(info.status()).isEqualTo(SubscriptionStatus.EXPIRED);
            assertThat(info.isActive()).isFalse();
        }
    }
}
