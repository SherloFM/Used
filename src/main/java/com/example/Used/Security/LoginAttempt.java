package com.example.Used.Security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class LoginAttempt {

    private LocalDateTime firstAttempt;
    private int attempts;

    public void pu() {
    }
}
