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
public class BusinessEntity extends BaseEntity {
    public static final int DEFAULT_FREE_SMS_CREDITS = 100;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, columnDefinition = "citext")
    private String slug;


    @Column(name = "free_sms_credits", nullable = false)
    @Builder.Default
    private int freeSmsCredits = DEFAULT_FREE_SMS_CREDITS;

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

    @Column(name = "terms_accepted_at")
    private OffsetDateTime termsAcceptedAt;


}
