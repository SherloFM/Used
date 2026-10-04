package com.example.Used.Controller;

import com.example.Used.Model.Requests.UserProfileRequests;
import com.example.Used.Model.UserProfile;
import com.example.Used.Service.UserProfileService;
import com.example.Used.Service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@AllArgsConstructor
@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private UserProfileService userProfileService;

    @GetMapping
    public UserProfile getProfile() {
        return userProfileService.getProfile();
    }

    @PutMapping
    public UserProfile updateProfile(
            @RequestBody UserProfileRequests request
    ) {
        return userProfileService.updateProfile(request);
    }

    @PutMapping("/image")
    public UserProfile uploadImage(
            @RequestParam("img") MultipartFile img
    ) throws IOException {
        return userProfileService.uploadImage(img);
    }
}
