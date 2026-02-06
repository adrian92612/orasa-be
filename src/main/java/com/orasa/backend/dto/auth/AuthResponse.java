package com.orasa.backend.dto.auth;

import java.util.List;
import java.util.UUID;

import com.orasa.backend.common.UserRole;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {

  private UUID userId;
  private String username;
  private UserRole role;
  private UUID businessId;
  private List<UUID> branchIds;
}
