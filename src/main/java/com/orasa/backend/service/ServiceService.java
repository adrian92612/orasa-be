package com.orasa.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.domain.Business;
import com.orasa.backend.domain.ServiceOffering;
import com.orasa.backend.domain.User;
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

    @Transactional
    public ServiceResponse createService(UUID actorUserId, UUID businessId, CreateServiceRequest request) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        ServiceOffering serviceOffering = ServiceOffering.builder()
                .businessId(businessId)
                .name(request.getName())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .durationMinutes(request.getDurationMinutes())
                .isAvailableGlobally(request.getAvailableGlobally())
                .build();

        ServiceOffering saved = serviceRepository.save(serviceOffering);
        
        // Log service creation
        activityLogService.logServiceCreated(actor, business, saved.getName());
        
        return mapToResponse(saved);
    }

    @Transactional
    public ServiceResponse updateService(UUID actorUserId, UUID serviceId, UUID businessId, UpdateServiceRequest request) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        ServiceOffering serviceOffering = serviceRepository.findById(serviceId)
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
        boolean beforeGlobal = serviceOffering.isAvailableGlobally();

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

        if (request.getAvailableGlobally() != null && request.getAvailableGlobally() != serviceOffering.isAvailableGlobally()) {
            changes.add(FieldChange.builder()
                    .field("Available Globally")
                    .before(beforeGlobal ? "Yes" : "No")
                    .after(request.getAvailableGlobally() ? "Yes" : "No")
                    .build());
            serviceOffering.setAvailableGlobally(request.getAvailableGlobally());
        }

        if (!changes.isEmpty()) {
            serviceOffering = serviceRepository.save(serviceOffering);
            
            // Log service update with details
            String details = FieldChange.toJson(changes);
            activityLogService.logServiceUpdated(actor, business, serviceOffering.getName(), details);
        }

        return mapToResponse(serviceOffering);
    }

    public List<ServiceResponse> getServicesByBusiness(UUID businessId) {
        return serviceRepository.findByBusinessId(businessId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ServiceResponse getServiceById(UUID serviceId) {
        ServiceOffering serviceOffering = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        return mapToResponse(serviceOffering);
    }

    @Transactional
    public void deleteService(UUID actorUserId, UUID serviceId, UUID businessId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        ServiceOffering serviceOffering = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if (!serviceOffering.getBusinessId().equals(businessId)) {
            throw new BusinessException("Service does not belong to your business");
        }
        
        // Log before deletion
        activityLogService.logServiceDeleted(actor, business, serviceOffering.getName());

        serviceRepository.delete(serviceOffering);
    }

    private ServiceResponse mapToResponse(ServiceOffering serviceOffering) {
        return ServiceResponse.builder()
                .id(serviceOffering.getId())
                .businessId(serviceOffering.getBusinessId())
                .name(serviceOffering.getName())
                .description(serviceOffering.getDescription())
                .basePrice(serviceOffering.getBasePrice())
                .durationMinutes(serviceOffering.getDurationMinutes())
                .availableGlobally(serviceOffering.isAvailableGlobally())
                .createdAt(serviceOffering.getCreatedAt())
                .updatedAt(serviceOffering.getUpdatedAt())
                .build();
    }
}


