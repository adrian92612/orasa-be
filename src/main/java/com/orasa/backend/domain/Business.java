package com.orasa.backend.domain;

import com.orasa.backend.common.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "businesses")
@Getter
@Setter
public class Business extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String slug;

    @Column(name = "free_sms_credits", nullable = false)
    private int freeSmsCredits = 100;

    @Column(name = "paid_sms_credits", nullable = false)
    private int paidSmsCredits = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.PENDING;

    @Column(name = "subscription_start_date")
    private OffsetDateTime subscriptionStartDate;

    @Column(name = "subscription_end_date")
    private OffsetDateTime subscriptionEndDate;
}
