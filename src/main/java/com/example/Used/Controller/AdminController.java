package com.example.Used.Controller;

import com.example.Used.Model.User;
import com.example.Used.Service.AdminService;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private AdminService adminService;

    @GetMapping("/users")
    public List<User> getAllUsers() {
        return adminService.getAllUsers();
    }

    @GetMapping("/users/{id}")
    public User getUserById(
            @PathVariable Long id
    ) {
        return adminService.getUserById(id);
    }

    @PutMapping("/users/{id}/ban")
    public User banUser(
            @PathVariable Long id
    ) {
        return adminService.banUser(id);
    }

    @PutMapping("/users/{id}/unban")
    public User unbanUser(
            @PathVariable Long id
    ) {
        return adminService.unbanUser(id);
    }

    @DeleteMapping("/users/{id}")
    public String deleteUser(
            @PathVariable Long id
    ) {
        adminService.deleteUser(id);

        return "User deleted successfully";
    }
}
