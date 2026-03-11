package com.orasa.backend.security;

import java.util.UUID;
import org.springframework.stereotype.Component;
import com.orasa.backend.common.UserRole;
import com.orasa.backend.domain.BranchEntity;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.exception.ForbiddenException;

@Component
public class SecurityValidator {

  public void validateBranchAccess(UserEntity user, BranchEntity branch) {
    validateBranchAccess(user, branch, null);
  }

  public void validateBranchAccess(UserEntity user, BranchEntity branch, String customMessage) {
    if (user.getRole() == UserRole.OWNER) {
      if (!branch.getBusiness().getId().equals(user.getBusiness().getId())) {
        throw new ForbiddenException(customMessage != null ? customMessage : "You do not have permission to access this branch");
      }
    } else if (user.getRole() == UserRole.STAFF) {
      boolean hasAccess = user.getBranches().stream()
          .anyMatch(b -> b.getId().equals(branch.getId()));
      if (!hasAccess) {
        throw new ForbiddenException(customMessage != null ? customMessage : "You are not assigned to this branch");
      }
    } else {
      throw new ForbiddenException("User role not authorized");
    }
  }

  public void validateBusinessAccess(UserEntity user, UUID businessId) {
    validateBusinessAccess(user, businessId, null);
  }

  public void validateBusinessAccess(UserEntity user, UUID businessId, String customMessage) {
    if (!user.getBusiness().getId().equals(businessId)) {
      throw new ForbiddenException(customMessage != null ? customMessage : "You do not have permission to access this business");
    }
  }

  public void validateOwnerOnly(UserEntity user, String action) {
    if (user.getRole() != UserRole.OWNER) {
      throw new ForbiddenException("Only business owners can " + action);
    }
  }
}
