package com.orasa.backend.dto.auth;

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
public class AuthResponse {

  private UUID userId;
  private String username;
  private UserRole role;
  private UUID businessId;
  private String businessName;
}
