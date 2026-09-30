package com.example.Used.Controller;

import com.example.Used.Model.Requests.LoginRequests;
import com.example.Used.Model.Requests.VerificationRequests;
import com.example.Used.Model.User;
import com.example.Used.Service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<?> loginUser(@RequestBody LoginRequests loginRequest){
        System.out.println("calling loginUser ==>");
        return userService.loginUser(loginRequest);
    }

}