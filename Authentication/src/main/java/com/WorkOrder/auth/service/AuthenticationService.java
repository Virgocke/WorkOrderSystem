package com.WorkOrder.auth.service;

import com.WorkOrder.auth.model.AuthenticatedUser;
import org.springframework.security.core.Authentication;

/**
 * @author Virgor
 * @date 2026年09月08日 23:43
 * @description
 */
public interface AuthenticationService {
    AuthenticatedUser toCurrentUser(Authentication authentication);
}
