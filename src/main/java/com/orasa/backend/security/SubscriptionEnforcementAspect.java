package com.orasa.backend.security;

import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.orasa.backend.common.RequiresActiveSubscription;
import com.orasa.backend.service.SubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionEnforcementAspect {

    private final SubscriptionService subscriptionService;

    @Around("@annotation(requiresActiveSubscription)")
    public Object enforceSubscription(ProceedingJoinPoint joinPoint, RequiresActiveSubscription requiresActiveSubscription) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            UUID businessId = user.businessId();
            
            if (businessId != null) {
                log.debug("Checking subscription for business {} (allowPending={})", 
                        businessId, requiresActiveSubscription.allowPending());
                subscriptionService.validateActiveSubscription(businessId, requiresActiveSubscription.allowPending());
            }
        }
        
        return joinPoint.proceed();
    }
}