package com.orasa.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.orasa.backend.common.CacheName;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.ServiceEntity;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.domain.BranchServiceEntity;
import com.orasa.backend.domain.BranchEntity;
import com.orasa.backend.dto.activity.FieldChange;
import com.orasa.backend.dto.service.CreateServiceRequest;
import com.orasa.backend.dto.service.ServiceResponse;
import com.orasa.backend.dto.service.UpdateServiceRequest;

import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BranchServiceRepository;
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
    private final BranchRepository branchRepository;
    private final BranchServiceRepository branchServiceRepository;
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
                .basePrice(request.getBasePrice())
                .durationMinutes(request.getDurationMinutes())
                .build();

        ServiceEntity saved = serviceRepository.save(serviceOffering);

        // Automatically assign to all branches
        List<BranchEntity> branches = branchRepository.findByBusinessId(businessId);
        List<BranchServiceEntity> branchServices = branches.stream()
                .map(branch -> BranchServiceEntity.builder()
                        .branchId(branch.getId())
                        .service(saved)
                        .isActive(true)
                        .build())
                .toList();
        branchServiceRepository.saveAll(branchServices);

        activityLogService.logServiceCreated(actor, business, saved.getName());
        
        cacheService.evictAll(CacheName.SERVICES);
        cacheService.evictAll(CacheName.BRANCH_SERVICES);
        cacheService.evict(CacheName.BRANCHES, businessId);
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
        BigDecimal beforePrice = serviceOffering.getBasePrice();
        Integer beforeDuration = serviceOffering.getDurationMinutes();

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

        if (request.getBasePrice() != null && !request.getBasePrice().equals(serviceOffering.getBasePrice())) {
            changes.add(FieldChange.builder()
                    .field("Base Price")
                    .before(beforePrice != null ? "₱" + beforePrice.toString() : "(not set)")
                    .after("₱" + request.getBasePrice().toString())
                    .build());
            serviceOffering.setBasePrice(request.getBasePrice());
        }

        if (request.getDurationMinutes() != null && !request.getDurationMinutes().equals(serviceOffering.getDurationMinutes())) {
            changes.add(FieldChange.builder()
                    .field("Duration")
                    .before(beforeDuration != null ? beforeDuration + " mins" : "(not set)")
                    .after(request.getDurationMinutes() + " mins")
                    .build());
            serviceOffering.setDurationMinutes(request.getDurationMinutes());
        }

        if (!changes.isEmpty()) {
            serviceOffering = serviceRepository.save(serviceOffering);
            
            // Log service update with details
            String details = FieldChange.toJson(changes);
            activityLogService.logServiceUpdated(actor, business, serviceOffering.getName(), details);

            cacheService.evictAll(CacheName.SERVICES);
            cacheService.evictAll(CacheName.BRANCH_SERVICES);
            cacheService.evict(CacheName.SERVICE, serviceId);
        }

        return mapToResponse(serviceOffering);
    }

    @Cacheable(value = CacheName.SERVICES, key = "{#businessId, #branchId}")
    public List<ServiceResponse> getServicesByBusiness(UUID businessId, UUID branchId) {
        List<ServiceEntity> services;
        if (branchId != null) {
            services = serviceRepository.findServicesForBranch(businessId, branchId);
        } else {
            services = serviceRepository.findByBusinessId(businessId);
        }
        return services.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Cacheable(value = CacheName.SERVICE, key = "#serviceId")
    public ServiceResponse getServiceById(UUID serviceId) {
        ServiceEntity serviceOffering = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

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

        List<BranchServiceEntity> branchLinks = branchServiceRepository.findByServiceId(serviceId);
        branchServiceRepository.deleteAll(branchLinks);

        serviceRepository.delete(serviceOffering);
        cacheService.evictAll(CacheName.SERVICES);
        cacheService.evictAll(CacheName.BRANCH_SERVICES);
        cacheService.evict(CacheName.BRANCHES, businessId);
        cacheService.evict(CacheName.SERVICE, serviceId);
    }

    private ServiceResponse mapToResponse(ServiceEntity serviceOffering) {
        return ServiceResponse.builder()
                .id(serviceOffering.getId())
                .businessId(serviceOffering.getBusinessId())
                .name(serviceOffering.getName())
                .description(serviceOffering.getDescription())
                .basePrice(serviceOffering.getBasePrice())
                .durationMinutes(serviceOffering.getDurationMinutes())
                .createdAt(serviceOffering.getCreatedAt())
                .updatedAt(serviceOffering.getUpdatedAt())
                .build();
    }
}


