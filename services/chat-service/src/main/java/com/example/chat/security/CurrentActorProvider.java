package com.example.chat.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.security.Principal;

@Component
public class CurrentActorProvider {
    public CurrentActor get(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CurrentActor actor) {
            return actor;
        }
        throw new AuthenticationCredentialsNotFoundException("Authentication required");
    }
}
