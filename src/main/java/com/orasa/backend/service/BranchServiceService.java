package com.orasa.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.domain.BranchService;
import com.orasa.backend.dto.service.AssignServiceToBranchRequest;
import com.orasa.backend.dto.service.BranchServiceResponse;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BranchServiceRepository;
import com.orasa.backend.repository.ServiceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchServiceService {

    private final BranchServiceRepository branchServiceRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;

    @Transactional
    public BranchServiceResponse assignServiceToBranch(UUID branchId, UUID businessId, AssignServiceToBranchRequest request) {
        var branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        if (!branch.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("Branch does not belong to your business");
        }

        var service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if (!service.getBusinessId().equals(businessId)) {
            throw new BusinessException("Service does not belong to your business");
        }

        var existingAssignments = branchServiceRepository.findByBranchId(branchId);
        boolean alreadyAssigned = existingAssignments.stream()
                .anyMatch(bs -> bs.getService().getId().equals(request.getServiceId()));

        if (alreadyAssigned) {
            throw new BusinessException("Service is already assigned to this branch");
        }

        BranchService branchService = BranchService.builder()
                .branchId(branchId)
                .service(service)
                .customPrice(request.getCustomPrice())
                .isActive(request.isActive())
                .build();

        BranchService saved = branchServiceRepository.save(branchService);
        return mapToResponse(saved);
    }

    public List<BranchServiceResponse> getServicesByBranch(UUID branchId) {
        return branchServiceRepository.findByBranchId(branchId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public BranchServiceResponse updateBranchService(UUID branchServiceId, UUID businessId, AssignServiceToBranchRequest request) {
        BranchService branchService = branchServiceRepository.findById(branchServiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch service assignment not found"));

        var branch = branchRepository.findById(branchService.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        if (!branch.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("Branch does not belong to your business");
        }

        if (request.getCustomPrice() != null) {
            branchService.setCustomPrice(request.getCustomPrice());
        }
        branchService.setActive(request.isActive());

        BranchService saved = branchServiceRepository.save(branchService);
        return mapToResponse(saved);
    }

    @Transactional
    public void removeServiceFromBranch(UUID branchServiceId, UUID businessId) {
        BranchService branchService = branchServiceRepository.findById(branchServiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch service assignment not found"));

        var branch = branchRepository.findById(branchService.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        if (!branch.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("Branch does not belong to your business");
        }

        branchServiceRepository.delete(branchService);
    }

    private BranchServiceResponse mapToResponse(BranchService branchService) {
        var service = branchService.getService();
        return BranchServiceResponse.builder()
                .id(branchService.getId())
                .branchId(branchService.getBranchId())
                .serviceId(service.getId())
                .serviceName(service.getName())
                .serviceDescription(service.getDescription())
                .basePrice(service.getBasePrice())
                .customPrice(branchService.getCustomPrice())
                .effectivePrice(branchService.getEffectivePrice())
                .durationMinutes(service.getDurationMinutes())
                .active(branchService.isActive())
                .createdAt(branchService.getCreatedAt())
                .build();
    }
}
