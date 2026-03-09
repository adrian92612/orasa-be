package com.orasa.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orasa.backend.common.UserRole;
import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.dto.export.ExportRequest;
import com.orasa.backend.exception.ForbiddenException;
import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.service.ExportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController extends BaseController {

    private final ExportService exportService;

    @PostMapping("/appointments")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> exportAppointments(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody ExportRequest request
    ) {
        if (authenticatedUser.role() != UserRole.OWNER) {
            throw new ForbiddenException("Only business owners can export appointments");
        }

        if (authenticatedUser.businessId() == null) {
            throw new ForbiddenException("Business setup required before exporting");
        }

        exportService.exportAppointments(
                authenticatedUser.businessId(),
                request.getMonth(),
                request.getYear()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Export started. You will receive progress updates via WebSocket."));
    }
}
