package com.orasa.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.dto.service.CreateServiceRequest;
import com.orasa.backend.dto.service.ServiceResponse;
import com.orasa.backend.dto.service.UpdateServiceRequest;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.service.ServiceService;
import com.orasa.backend.common.RequiresActiveSubscription;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
public class ServiceController extends BaseController {

    private final ServiceService serviceService;

    @PostMapping
    @RequiresActiveSubscription
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ServiceResponse>> createService(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateServiceRequest request
    ) {
        validateBusinessExists(authenticatedUser);

        ServiceResponse service = serviceService.createService(
                authenticatedUser.userId(),
                authenticatedUser.businessId(),
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Service created successfully", service));
    }

    @PutMapping("/{serviceId}")
    @RequiresActiveSubscription
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ServiceResponse>> updateService(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateServiceRequest request
    ) {
        validateBusinessExists(authenticatedUser);

        ServiceResponse service = serviceService.updateService(
                authenticatedUser.userId(),
                serviceId,
                authenticatedUser.businessId(),
                request
        );

        return ResponseEntity.ok(ApiResponse.success("Service updated successfully", service));
    }

    @GetMapping
    @PreAuthorize("hasRole('OWNER') or hasRole('STAFF')")
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getMyServices(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) UUID branchId
    ) {
        validateBusinessExists(authenticatedUser);

        List<ServiceResponse> services = serviceService.getServicesByBusiness(authenticatedUser.businessId(), branchId);
        return ResponseEntity.ok(ApiResponse.success(services));
    }

    @GetMapping("/{serviceId}")
    @PreAuthorize("hasRole('OWNER') or hasRole('STAFF')")
    public ResponseEntity<ApiResponse<ServiceResponse>> getServiceById(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID serviceId
    ) {
        ServiceResponse service = serviceService.getServiceById(serviceId);

        if (!service.getBusinessId().equals(authenticatedUser.businessId())) {
            throw new BusinessException("Service does not belong to your business");
        }

        return ResponseEntity.ok(ApiResponse.success(service));
    }

    @DeleteMapping("/{serviceId}")
    @RequiresActiveSubscription
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Void>> deleteService(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID serviceId
    ) {
        validateBusinessExists(authenticatedUser);

        serviceService.deleteService(authenticatedUser.userId(), serviceId, authenticatedUser.businessId());
        return ResponseEntity.ok(ApiResponse.success("Service deleted successfully"));
    }
}
