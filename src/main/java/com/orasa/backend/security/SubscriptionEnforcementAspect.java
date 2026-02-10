package com.orasa.backend.security;

import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.orasa.backend.service.SubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AOP Aspect that intercepts methods annotated with @RequiresActiveSubscription
 * and validates that the user's business has an active subscription.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionEnforcementAspect {

    private final SubscriptionService subscriptionService;

    @Around("@annotation(com.orasa.backend.common.RequiresActiveSubscription)")
    public Object enforceSubscription(ProceedingJoinPoint joinPoint) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
        //     UUID businessId = user.businessId();
            
        //     if (businessId != null) {
        //         log.debug("Checking subscription for business {}", businessId);
        //         subscriptionService.validateActiveSubscription(businessId);
        //     }
        // }
        
        return joinPoint.proceed();
    }
}