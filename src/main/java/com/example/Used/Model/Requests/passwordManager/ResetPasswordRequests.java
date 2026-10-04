package com.example.Used.Model.Requests.passwordManager;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequests {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Reset code is required")
    private String code;

    @NotBlank(message = "New password is required")
    private String newPassword;
}
