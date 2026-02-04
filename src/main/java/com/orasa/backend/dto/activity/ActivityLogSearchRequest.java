package com.orasa.backend.dto.activity;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Data;

@Data
public class ActivityLogSearchRequest {
    private UUID branchId;
    private String action;
    private LocalDate startDate;
    private LocalDate endDate;
}
