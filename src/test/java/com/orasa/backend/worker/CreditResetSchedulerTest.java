package com.orasa.backend.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.service.SubscriptionService;

@ExtendWith(MockitoExtension.class)
public class CreditResetSchedulerTest {

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private Clock clock;

    @InjectMocks
    private CreditResetScheduler creditResetScheduler;

    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        now = OffsetDateTime.of(2026, 3, 2, 8, 0, 0, 0, ZoneOffset.UTC);
    }

    @Test
    @DisplayName("Should process overdue credit resets when found")
    void recoverOverdueResets_withBusinesses_success() {
        // Arrange
        when(clock.instant()).thenReturn(now.toInstant());
        when(clock.getZone()).thenReturn(now.getOffset());

        BusinessEntity business = BusinessEntity.builder()
            .name("Overdue Business")
            .build();
        business.setId(UUID.randomUUID());

        when(businessRepository.findBusinessesDueForCreditReset(now)).thenReturn(List.of(business));

        // Act
        creditResetScheduler.recoverOverdueResets();

        // Assert
        verify(subscriptionService, times(1)).checkAndRefreshCredits(business);
    }

    @Test
    @DisplayName("Should do nothing when no overdue credit resets exist")
    void recoverOverdueResets_empty_doesNothing() {
        // Arrange
        when(clock.instant()).thenReturn(now.toInstant());
        when(clock.getZone()).thenReturn(now.getOffset());
        when(businessRepository.findBusinessesDueForCreditReset(now)).thenReturn(Collections.emptyList());

        // Act
        creditResetScheduler.recoverOverdueResets();

        // Assert
        verify(subscriptionService, never()).checkAndRefreshCredits(any());
    }
}
