package com.example.Used.Security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final int window = 15;

    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    public boolean isAllowed(String email) {

        LoginAttempt attempt = loginAttempts.get(email);

        if (attempt == null) {
            return true;
        }

        if (attempt.getFirstAttempt().plusMinutes(window).isBefore(LocalDateTime.now())){
            loginAttempts.remove(email);
        }

        return attempt.getAttempts() < MAX_ATTEMPTS;
    }

    public void recordFailedAttempt(String email) {
        LoginAttempt attempt = loginAttempts.get(email);

        if (attempt == null){
            loginAttempts.put(email,new LoginAttempt(LocalDateTime.now(),1));
        }else {
            attempt.setAttempts(attempt.getAttempts() + 1);
        }
    }

    public void resetAttempts(String email) {
        loginAttempts.remove(email);
    }

}