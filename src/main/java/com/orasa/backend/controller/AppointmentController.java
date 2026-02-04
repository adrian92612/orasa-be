package com.orasa.backend.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import com.orasa.backend.common.RequiresActiveSubscription;
import com.orasa.backend.dto.appointment.AppointmentResponse;
import com.orasa.backend.dto.appointment.CreateAppointmentRequest;
import com.orasa.backend.dto.appointment.UpdateAppointmentRequest;
import com.orasa.backend.dto.appointment.UpdateResult;
import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.service.AppointmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {
  private final AppointmentService appointmentService;
  
  @PostMapping
  @RequiresActiveSubscription
  public ResponseEntity<ApiResponse<AppointmentResponse>> createAppointment(
    @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
    @Valid @RequestBody CreateAppointmentRequest request
  ) {
    AppointmentResponse response = appointmentService.createAppointment(authenticatedUser.userId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Appointment created successfully",response));
  }

  @PutMapping("/{id}")
  @RequiresActiveSubscription
  public ResponseEntity<ApiResponse<AppointmentResponse>> updateAppointment(
    @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
    @PathVariable UUID id,
    @Valid @RequestBody UpdateAppointmentRequest request
  ) {
    UpdateResult result = appointmentService.updateAppointment(authenticatedUser.userId(), id, request);
    String message = result.isModified() ? "Appointment updated successfully" : "No changes made";
    return ResponseEntity.ok(ApiResponse.success(message, result.getAppointment()));
  }

  @GetMapping("/branch/{branchId}")
  public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getAppointmentsByBranch(
    @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
    @PathVariable UUID branchId,
    @PageableDefault(size = 20) Pageable pageable
  ) {
    Page<AppointmentResponse> appointments = appointmentService.getAppointmentsByBranch(authenticatedUser.userId(), branchId, pageable);
    return ResponseEntity.ok(ApiResponse.success("Appointments retrieved successfully", appointments));
  }

  @GetMapping("/business/{businessId}")
  public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getAppointmentsByBusiness(
    @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
    @PathVariable UUID businessId,
    @PageableDefault(size = 20) Pageable pageable
  ) {
    Page<AppointmentResponse> appointments = appointmentService.getAppointmentsByBusiness(authenticatedUser.userId(), businessId, pageable);
    return ResponseEntity.ok(ApiResponse.success("Appointments retrieved successfully", appointments)); 
  }

  @GetMapping("/branch/{branchId}/search")
  public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> searchAppointmentsByBranch(
    @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
    @PathVariable UUID branchId,
    @RequestParam(required = false, defaultValue = "") String search,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
    @PageableDefault(size = 20) Pageable pageable
  ) {
    Page<AppointmentResponse> appointments = appointmentService.searchAppointments(authenticatedUser.userId(), branchId, search, startDate, endDate, pageable);
    return ResponseEntity.ok(ApiResponse.success("Appointments retrieved successfully", appointments));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointmentById(
    @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
    @PathVariable UUID id
  ) {
    AppointmentResponse appointment = appointmentService.getAppointmentById(authenticatedUser.userId(), id);
    return ResponseEntity.ok(ApiResponse.success("Appointment retrieved successfully", appointment));
  }

  @DeleteMapping("/{id}")
  @RequiresActiveSubscription
  public ResponseEntity<ApiResponse<Void>> deleteAppointment(
    @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
    @PathVariable UUID id
  ) {
    appointmentService.deleteAppointment(authenticatedUser.userId(), id);
    return ResponseEntity.ok(ApiResponse.success("Appointment deleted successfully"));
  }
}
