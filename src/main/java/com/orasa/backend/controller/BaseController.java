package com.orasa.backend.controller;

import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.security.AuthenticatedUser;

public abstract class BaseController {

    protected void validateBusinessExists(AuthenticatedUser user) {
        if (user.businessId() == null) {
            throw new BusinessException("Business must be created first");
        }
    }
}
