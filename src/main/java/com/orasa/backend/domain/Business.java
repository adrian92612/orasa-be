package com.orasa.backend.domain;

import com.orasa.backend.common.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;

@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE businesses SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class Business extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String slug;

    @Column(name = "free_sms_credits", nullable = false)
    @Builder.Default
    private int freeSmsCredits = 100;

    @Column(name = "paid_sms_credits", nullable = false)
    @Builder.Default
    private int paidSmsCredits = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false)
    @Builder.Default
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.PENDING;

    @Column(name = "subscription_start_date")
    private OffsetDateTime subscriptionStartDate;

    @Column(name = "subscription_end_date")
    private OffsetDateTime subscriptionEndDate;

    @Column(name = "next_credit_reset_date")
    private OffsetDateTime nextCreditResetDate;

    @Column(name = "is_onboarding_completed", nullable = false)
    @Builder.Default
    private boolean isOnboardingCompleted = false;

    /**
     * Checks if subscription is currently active.
     * Note: This is a simple check. Use SubscriptionService for full validation with auto-expiry.
     */
    public boolean hasActiveSubscription() {
        if (subscriptionStatus != SubscriptionStatus.ACTIVE) {
            return false;
        }
        if (subscriptionEndDate != null && subscriptionEndDate.isBefore(OffsetDateTime.now())) {
            return false;
        }
        return true;
    }
}
