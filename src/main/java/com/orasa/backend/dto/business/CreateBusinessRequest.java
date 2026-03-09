package com.orasa.backend.dto.business;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(max = 35, message = "Business name must not exceed 35 characters")
    @Pattern(regexp = "^[a-zA-Z0-9 ]*$", message = "Only alphanumeric characters and spaces are allowed")
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
        @Size(max = 35, message = "Branch name must not exceed 35 characters")
        @Pattern(regexp = "^[a-zA-Z0-9 ]*$", message = "Only alphanumeric characters and spaces are allowed")
        private String name;

        private String address;

        private String phoneNumber;
    }
}
