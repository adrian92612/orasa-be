package com.orasa.backend.service;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.orasa.backend.common.CacheName;
import com.orasa.backend.config.CacheBusinessId;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.ServiceEntity;
import com.orasa.backend.domain.UserEntity;

import com.orasa.backend.dto.activity.FieldChange;
import com.orasa.backend.dto.service.CreateServiceRequest;
import com.orasa.backend.dto.service.ServiceResponse;
import com.orasa.backend.dto.service.UpdateServiceRequest;

import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.exception.ResourceNotFoundException;

import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.ServiceRepository;
import com.orasa.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final ActivityLogService activityLogService;
    private final CacheService cacheService;

    @Transactional
    public ServiceResponse createService(UUID actorUserId, UUID businessId, CreateServiceRequest request) {
        UserEntity actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        ServiceEntity serviceOffering = ServiceEntity.builder()
                .businessId(businessId)
                .name(request.getName())
                .description(request.getDescription())
                .build();

        ServiceEntity saved = serviceRepository.save(serviceOffering);

        activityLogService.logServiceCreated(actor, business, saved.getName());
        
        cacheService.evictAll(CacheName.SERVICES, businessId);
        cacheService.evictAll(CacheName.BRANCHES, businessId);
        cacheService.evictAll(CacheName.BRANCH, businessId);
        cacheService.evictAll(CacheName.USER_BRANCHES, businessId);
        return mapToResponse(saved);
    }

    @Transactional
    public ServiceResponse updateService(UUID actorUserId, UUID serviceId, UUID businessId, UpdateServiceRequest request) {
        UserEntity actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        ServiceEntity serviceOffering = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if (!serviceOffering.getBusinessId().equals(businessId)) {
            throw new BusinessException("Service does not belong to your business");
        }

        // Track changes
        List<FieldChange> changes = new ArrayList<>();
        String beforeName = serviceOffering.getName();
        String beforeDescription = serviceOffering.getDescription();

        if (request.getName() != null && !request.getName().equals(serviceOffering.getName())) {
            changes.add(FieldChange.builder()
                    .field("Name")
                    .before(beforeName)
                    .after(request.getName())
                    .build());
            serviceOffering.setName(request.getName());
        }

        if (request.getDescription() != null && !request.getDescription().equals(serviceOffering.getDescription())) {
            changes.add(FieldChange.builder()
                    .field("Description")
                    .before(beforeDescription != null ? beforeDescription : "(empty)")
                    .after(request.getDescription())
                    .build());
            serviceOffering.setDescription(request.getDescription());
        }

        if (!changes.isEmpty()) {
            serviceOffering = serviceRepository.save(serviceOffering);
            
            // Log service update with details
            String details = FieldChange.toJson(changes);
            activityLogService.logServiceUpdated(actor, business, serviceOffering.getName(), details);

            cacheService.evictAll(CacheName.SERVICES, businessId);
            cacheService.evict(CacheName.SERVICE, businessId + CacheName.SEPARATOR + serviceId + CacheName.SUFFIX_DETAILS);
            cacheService.evictAll(CacheName.BRANCHES, businessId);
            cacheService.evictAll(CacheName.BRANCH, businessId);
            cacheService.evictAll(CacheName.USER_BRANCHES, businessId);
        }

        return mapToResponse(serviceOffering);
    }

    @Cacheable(value = CacheName.SERVICES, keyGenerator = "businessKeyGenerator")
    public List<ServiceResponse> getServicesByBusiness(@CacheBusinessId UUID businessId, UUID branchId) {
        List<ServiceEntity> services = serviceRepository.findByBusinessId(businessId);
        return services.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Cacheable(value = CacheName.SERVICE, keyGenerator = "businessKeyGenerator")
    public ServiceResponse getServiceById(UUID serviceId, @CacheBusinessId UUID businessId) {
        ServiceEntity serviceOffering = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if (!serviceOffering.getBusinessId().equals(businessId)) {
            throw new ResourceNotFoundException("Service not found in your business");
        }

        return mapToResponse(serviceOffering);
    }

    @Transactional
    public void deleteService(UUID actorUserId, UUID serviceId, UUID businessId) {
        UserEntity actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        ServiceEntity serviceOffering = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if (!serviceOffering.getBusinessId().equals(businessId)) {
            throw new BusinessException("Service does not belong to your business");
        }
        
        activityLogService.logServiceDeleted(actor, business, serviceOffering.getName());

        serviceRepository.delete(serviceOffering);
        cacheService.evictAll(CacheName.SERVICES, businessId);
        cacheService.evictAll(CacheName.BRANCHES, businessId);
        cacheService.evictAll(CacheName.BRANCH, businessId);
        cacheService.evictAll(CacheName.USER_BRANCHES, businessId);
        cacheService.evict(CacheName.SERVICE, businessId + CacheName.SEPARATOR + serviceId + CacheName.SUFFIX_DETAILS);
    }

    private ServiceResponse mapToResponse(ServiceEntity serviceOffering) {
        return ServiceResponse.builder()
                .id(serviceOffering.getId())
                .businessId(serviceOffering.getBusinessId())
                .name(serviceOffering.getName())
                .description(serviceOffering.getDescription())
                .createdAt(serviceOffering.getCreatedAt())
                .updatedAt(serviceOffering.getUpdatedAt())
                .build();
    }
}


