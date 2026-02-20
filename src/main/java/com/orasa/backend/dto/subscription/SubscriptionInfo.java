package com.orasa.backend.dto.subscription;

import java.time.OffsetDateTime;
import com.orasa.backend.common.SubscriptionStatus;

public record SubscriptionInfo(
    SubscriptionStatus status,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    boolean isActive
) {}
