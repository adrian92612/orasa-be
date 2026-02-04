package com.orasa.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.dto.service.CreateServiceRequest;
import com.orasa.backend.dto.service.ServiceResponse;
import com.orasa.backend.dto.service.UpdateServiceRequest;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.ServiceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceService {

    private final ServiceRepository serviceRepository;

    @Transactional
    public ServiceResponse createService(UUID businessId, CreateServiceRequest request) {
        com.orasa.backend.domain.Service service = com.orasa.backend.domain.Service.builder()
                .businessId(businessId)
                .name(request.getName())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .durationMinutes(request.getDurationMinutes())
                .isAvailableGlobally(request.isAvailableGlobally())
                .build();

        com.orasa.backend.domain.Service saved = serviceRepository.save(service);
        return mapToResponse(saved);
    }

    @Transactional
    public ServiceResponse updateService(UUID serviceId, UUID businessId, UpdateServiceRequest request) {
        com.orasa.backend.domain.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if (!service.getBusinessId().equals(businessId)) {
            throw new BusinessException("Service does not belong to your business");
        }

        boolean hasChanges = false;

        if (request.getName() != null && !request.getName().equals(service.getName())) {
            service.setName(request.getName());
            hasChanges = true;
        }

        if (request.getDescription() != null && !request.getDescription().equals(service.getDescription())) {
            service.setDescription(request.getDescription());
            hasChanges = true;
        }

        if (request.getBasePrice() != null && !request.getBasePrice().equals(service.getBasePrice())) {
            service.setBasePrice(request.getBasePrice());
            hasChanges = true;
        }

        if (request.getDurationMinutes() != null && !request.getDurationMinutes().equals(service.getDurationMinutes())) {
            service.setDurationMinutes(request.getDurationMinutes());
            hasChanges = true;
        }

        if (request.getAvailableGlobally() != null && request.getAvailableGlobally() != service.isAvailableGlobally()) {
            service.setAvailableGlobally(request.getAvailableGlobally());
            hasChanges = true;
        }

        if (hasChanges) {
            service = serviceRepository.save(service);
        }

        return mapToResponse(service);
    }

    public List<ServiceResponse> getServicesByBusiness(UUID businessId) {
        return serviceRepository.findByBusinessId(businessId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ServiceResponse getServiceById(UUID serviceId) {
        com.orasa.backend.domain.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        return mapToResponse(service);
    }

    @Transactional
    public void deleteService(UUID serviceId, UUID businessId) {
        com.orasa.backend.domain.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if (!service.getBusinessId().equals(businessId)) {
            throw new BusinessException("Service does not belong to your business");
        }

        serviceRepository.delete(service);
    }

    private ServiceResponse mapToResponse(com.orasa.backend.domain.Service service) {
        return ServiceResponse.builder()
                .id(service.getId())
                .businessId(service.getBusinessId())
                .name(service.getName())
                .description(service.getDescription())
                .basePrice(service.getBasePrice())
                .durationMinutes(service.getDurationMinutes())
                .availableGlobally(service.isAvailableGlobally())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }
}
