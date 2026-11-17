package com.example.accounts.model.requests.passwordmanager;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgetPasswordRequests {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}