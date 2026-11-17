package com.example.marketplace.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Replaces the old Accounts-owned CurrentUserService for authenticated callers. */
@Component
public class CurrentActorProvider {

    public CurrentActor get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentActor actor)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return actor;
    }
}
