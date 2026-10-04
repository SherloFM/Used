package com.example.Used.Controller;

import com.example.Used.Model.Requests.LoginRequests;
import com.example.Used.Model.Requests.VerificationRequests;
import com.example.Used.Model.Requests.passwordManager.ChangePasswordRequests;
import com.example.Used.Model.Requests.passwordManager.ForgetPasswordRequests;
import com.example.Used.Model.Requests.passwordManager.ResetPasswordRequests;
import com.example.Used.Model.User;
import com.example.Used.Service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping(path = "/auth/user")
public class UserController {

    private UserService userService;

    @PostMapping("/register")
    public User register(
            @RequestBody User userObject
    ){
        return userService.createUser(userObject);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyEmail(
            @RequestBody VerificationRequests request
    ){
        return userService.verifyEmail(request);
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(
            @Valid @RequestBody LoginRequests loginRequest
    ){
        return userService.loginUser(loginRequest);
    }

    @PostMapping("/forget-password")
    public ResponseEntity<?> forgotPassword(
            @Valid @RequestBody ForgetPasswordRequests request
    ) {
        return userService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody ResetPasswordRequests request
    ) {
        return userService.resetPassword(request);
    }

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequests request
    ) {
        return userService.changePassword(request);
    }

}