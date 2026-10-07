package com.example.Used.Controller;

import com.example.Used.Model.AuditLog;
import com.example.Used.Model.Requests.UserProfileRequests;
import com.example.Used.Model.User;
import com.example.Used.Model.UserProfile;
import com.example.Used.Service.AuditLogService;
import com.example.Used.Service.CurrentUserService;
import com.example.Used.Service.UserProfileService;
import com.example.Used.Service.UserService;
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
    private AuditLogService auditLogService;

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
        return userProfileService.uploadProfileImage(img);
    }

    @GetMapping("/activity")
    public List<AuditLog> getActivityHistory() {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }
        // Fetch logs where actorId matches current user ID
        return auditLogService.getUserActivity(currentUser.getId());
    }
}