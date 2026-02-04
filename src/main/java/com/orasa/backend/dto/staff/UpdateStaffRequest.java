package com.orasa.backend.dto.staff;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStaffRequest {

    private String email;

    @Size(min = 6, message = "Password must be at least 6 characters")
    private String newPassword;

    private List<UUID> branchIds;
}
