package com.orasa.backend.dto.business;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBusinessRequest {

    @NotBlank(message = "Business name is required")
    private String name;

    @NotNull(message = "Terms acceptance timestamp is required")
    private OffsetDateTime termsAcceptedAt;

    @NotNull(message = "First branch is required")
    @Valid
    private BranchData branch;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchData {
        @NotBlank(message = "Branch name is required")
        private String name;

        private String address;

        private String phoneNumber;
    }
}
