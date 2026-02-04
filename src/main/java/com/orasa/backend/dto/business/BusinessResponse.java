package com.orasa.backend.dto.business;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.orasa.backend.common.SubscriptionStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessResponse {
    private UUID id;
    private String name;
    private String slug;
    private int freeSmsCredits;
    private int paidSmsCredits;
    private SubscriptionStatus subscriptionStatus;
    private OffsetDateTime subscriptionStartDate;
    private OffsetDateTime subscriptionEndDate;
    private OffsetDateTime createdAt;

    // Included when business is created with a branch (onboarding)
    private UUID firstBranchId;
}
