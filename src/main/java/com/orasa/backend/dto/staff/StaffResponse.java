package com.orasa.backend.dto.staff;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.orasa.backend.common.UserRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffResponse {
    private UUID id;
    private UUID businessId;
    private String username;
    private String email;
    private UserRole role;

    private List<BranchInfo> branches;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchInfo {
        private UUID id;
        private String name;
    }
}
