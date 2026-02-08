package com.orasa.backend.dto.branch;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBranchRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String address;

    private String phoneNumber;

    private java.util.Set<java.util.UUID> staffIds;

    private java.util.Set<java.util.UUID> serviceIds;
}
