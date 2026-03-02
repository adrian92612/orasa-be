package com.orasa.backend.security;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.orasa.backend.common.RequiresActiveSubscription;
import com.orasa.backend.service.SubscriptionService;

@ExtendWith(MockitoExtension.class)
class SubscriptionEnforcementAspectTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private RequiresActiveSubscription requiresActiveSubscription;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private SubscriptionEnforcementAspect aspect;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enforceSubscription_withAuthenticatedUser_shouldValidateSubscription() throws Throwable {
        // Arrange
        UUID businessId = UUID.randomUUID();
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        when(user.businessId()).thenReturn(businessId);
        
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(requiresActiveSubscription.allowPending()).thenReturn(false);
        
        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);
        
        doNothing().when(subscriptionService).validateActiveSubscription(businessId, false);

        // Act
        Object result = aspect.enforceSubscription(joinPoint, requiresActiveSubscription);

        // Assert
        assertEquals(expectedResult, result);
        verify(subscriptionService).validateActiveSubscription(businessId, false);
        verify(joinPoint).proceed();
    }

    @Test
    void enforceSubscription_withNoAuthentication_shouldProceedWithoutValidation() throws Throwable {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(null);
        
        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // Act
        Object result = aspect.enforceSubscription(joinPoint, requiresActiveSubscription);

        // Assert
        assertEquals(expectedResult, result);
        verify(joinPoint).proceed();
    }
}
