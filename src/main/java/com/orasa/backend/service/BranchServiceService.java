package com.orasa.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.orasa.backend.common.CacheName;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.domain.BranchEntity;
import com.orasa.backend.domain.BranchServiceEntity;
import com.orasa.backend.domain.ServiceEntity;
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
    private final CacheService cacheService;

    @Transactional
    public BranchServiceResponse assignServiceToBranch(UUID branchId, UUID businessId, AssignServiceToBranchRequest request) {
        BranchEntity branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        if (!branch.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("Branch does not belong to your business");
        }

        ServiceEntity service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if (!service.getBusinessId().equals(businessId)) {
            throw new BusinessException("Service does not belong to your business");
        }

        List<BranchServiceEntity> existingAssignments = branchServiceRepository.findByBranchId(branchId);
        boolean alreadyAssigned = existingAssignments.stream()
                .anyMatch(bs -> bs.getService().getId().equals(request.getServiceId()));

        if (alreadyAssigned) {
            throw new BusinessException("Service is already assigned to this branch");
        }

        BranchServiceEntity branchService = BranchServiceEntity.builder()
                .branchId(branchId)
                .service(service)
                .customPrice(request.getCustomPrice())
                .isActive(request.getActive())
                .build();

        BranchServiceEntity saved = branchServiceRepository.save(branchService);
        cacheService.evict(CacheName.BRANCH_SERVICES, branchId);
        cacheService.evictAll(CacheName.SERVICES);
        cacheService.evict(CacheName.BRANCHES, businessId);
        cacheService.evict(CacheName.BRANCH, branchId);
        cacheService.evictAll(CacheName.USER_BRANCHES);
        return mapToResponse(saved);
    }

    @Cacheable(value = CacheName.BRANCH_SERVICES, key = "#branchId")
    public List<BranchServiceResponse> getServicesByBranch(UUID branchId) {
        return branchServiceRepository.findByBranchId(branchId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public BranchServiceResponse updateBranchService(UUID branchServiceId, UUID businessId, AssignServiceToBranchRequest request) {
        BranchServiceEntity branchService = branchServiceRepository.findById(branchServiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch service assignment not found"));

        BranchEntity branch = branchRepository.findById(branchService.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        if (!branch.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("Branch does not belong to your business");
        }

        if (request.getCustomPrice() != null) {
            branchService.setCustomPrice(request.getCustomPrice());
        }
        branchService.setActive(request.getActive());

        BranchServiceEntity saved = branchServiceRepository.save(branchService);
        cacheService.evict(CacheName.BRANCH_SERVICES, branchService.getBranchId());
        cacheService.evictAll(CacheName.SERVICES);
        cacheService.evict(CacheName.BRANCHES, businessId);
        cacheService.evict(CacheName.BRANCH, branchService.getBranchId());
        cacheService.evictAll(CacheName.USER_BRANCHES);
        return mapToResponse(saved);
    }

    @Transactional
    public void removeServiceFromBranch(UUID branchServiceId, UUID businessId) {
        BranchServiceEntity branchService = branchServiceRepository.findById(branchServiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch service assignment not found"));

        BranchEntity branch = branchRepository.findById(branchService.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        if (!branch.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("Branch does not belong to your business");
        }

        branchServiceRepository.delete(branchService);
        cacheService.evict(CacheName.BRANCH_SERVICES, branchService.getBranchId());
        cacheService.evictAll(CacheName.SERVICES);
        cacheService.evict(CacheName.BRANCHES, businessId);
        cacheService.evict(CacheName.BRANCH, branchService.getBranchId());
        cacheService.evictAll(CacheName.USER_BRANCHES);
    }

    private BranchServiceResponse mapToResponse(BranchServiceEntity branchService) {
        ServiceEntity service = branchService.getService();
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
