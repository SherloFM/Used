package com.example.accounts.controller;

import com.example.accounts.client.AuditLogEntry;
import com.example.accounts.client.AuditClient;
import com.example.accounts.model.requests.UserProfileRequests;
import com.example.accounts.model.responses.ProfileResponse;
import com.example.accounts.model.User;
import com.example.accounts.service.CurrentUserService;
import com.example.accounts.service.UserProfileService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private UserProfileService userProfileService;
    private CurrentUserService currentUserService;
    private AuditClient auditClient;

    @GetMapping
    public ProfileResponse getProfile() {
        return userProfileService.getProfile();
    }

    @PutMapping
    public ProfileResponse updateProfile(
            @RequestBody UserProfileRequests request
    ) {
        return userProfileService.updateProfile(request);
    }

    @PutMapping("/image")
    public ProfileResponse uploadImage(
            @RequestParam("img") MultipartFile img
    ) throws IOException {
        return userProfileService.uploadProfileImage(img);
    }

    @GetMapping("/activity")
    public List<AuditLogEntry> getActivityHistory() {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }
        // Fetch logs where actorId matches current user ID
        return auditClient.getUserActivity(currentUser.getId());
    }
}
