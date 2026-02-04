package com.orasa.backend.dto.activity;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogResponse {
    private UUID id;
    private UUID userId;
    private String userName;
    private String userRole;
    private UUID businessId;
    private UUID branchId;
    private String branchName;
    private String action;

    private String description;
    private String details;

    private OffsetDateTime createdAt;
}

