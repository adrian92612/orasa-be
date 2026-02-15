package com.orasa.backend.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Methods annotated with this will require an active subscription.
 * If the business subscription is expired or cancelled, a 402 response is returned.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresActiveSubscription {
    /**
     * If true, allows the operation even if the subscription status is PENDING.
     * Useful for onboarding flows.
     */
    boolean allowPending() default false;
}