package com.orasa.backend.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orasa.backend.dto.appointment.AppointmentResponse;
import com.orasa.backend.dto.appointment.CreateAppointmentRequest;
import com.orasa.backend.dto.appointment.UpdateAppointmentRequest;
import com.orasa.backend.dto.appointment.UpdateResult;
import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.service.AppointmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {
  private final AppointmentService appointmentService;
  
  @PostMapping
  public ResponseEntity<ApiResponse<AppointmentResponse>> createAppointment(
    @Valid @RequestBody CreateAppointmentRequest request
  ) {
    AppointmentResponse response = appointmentService.createAppointment(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Appointment created successfully",response));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<AppointmentResponse>> updateAppointment(
    @PathVariable UUID id,
    @Valid @RequestBody UpdateAppointmentRequest request
  ) {
    UpdateResult result = appointmentService.updateAppointment(id, request);
    String message = result.isModified() ? "Appointment updated successfully" : "No changes made";
    return ResponseEntity.ok(ApiResponse.success(message, result.getAppointment()));
  }

  @GetMapping("/branch/{branchId}")
  public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getAppointmentsByBranch(
    @PathVariable UUID branchId,
    @PageableDefault(size = 20) Pageable pageable
  ) {
    Page<AppointmentResponse> appointments = appointmentService.getAppointmentsByBranch(branchId, pageable);
    return ResponseEntity.ok(ApiResponse.success("Appointments retrieved successfully", appointments));
  }

  @GetMapping("/business/{businessId}")
  public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getAppointmentsByBusiness(
    @PathVariable UUID businessId,
    @PageableDefault(size = 20) Pageable pageable
  ) {
    Page<AppointmentResponse> appointments = appointmentService.getAppointmentsByBusiness(businessId, pageable);
    return ResponseEntity.ok(ApiResponse.success("Appointments retrieved successfully", appointments)); 
  }

  @GetMapping("/branch/{branchId}/search")
  public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> searchAppointmentsByBranch(
    @PathVariable UUID branchId,
    @RequestParam(required = false, defaultValue = "") String search,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
    @PageableDefault(size = 20) Pageable pageable
  ) {
    Page<AppointmentResponse> appointments = appointmentService.searchAppointments(branchId, search, startDate, endDate, pageable);
    return ResponseEntity.ok(ApiResponse.success("Appointments retrieved successfully", appointments));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointmentById(
    @PathVariable UUID id
  ) {
    AppointmentResponse appointment = appointmentService.getAppointmentById(id);
    return ResponseEntity.ok(ApiResponse.success("Appointment retrieved successfully", appointment));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteAppointment(
    @PathVariable UUID id
  ) {
    appointmentService.deleteAppointment(id);
    return ResponseEntity.ok(ApiResponse.success("Appointment deleted successfully"));
  }
}
