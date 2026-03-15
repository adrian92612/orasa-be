package com.orasa.backend.dto.branch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(max = 35, message = "Branch name must not exceed 35 characters")
    @Pattern(regexp = "^[a-zA-Z0-9 ]*$", message = "Only alphanumeric characters and spaces are allowed")
    private String name;

    private String address;

    private String phoneNumber;

    private java.util.Set<java.util.UUID> staffIds;
}
