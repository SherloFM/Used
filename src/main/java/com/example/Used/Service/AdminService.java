package com.example.Used.Service;

import com.example.Used.Model.AuditLog;
import com.example.Used.Model.User;
import com.example.Used.Repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;
    private final EmailServices emailServices;

    public AdminService(
            UserRepository userRepository,
            AuditLogService auditLogService,
            CurrentUserService currentUserService,
            EmailServices emailServices
    ) {

        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.emailServices  = emailServices;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
    }

    public User banUser(Long id) {

        User user = getUserById(id);
        String userEmail = user.getEmail();

        user.setBanned(true);
        auditLogService.log(
                AuditLog.AuditAction.USER_BANNED, currentUserService.getCurrentUser(),
                "Banned user id=" + user.getId() + " (" + user.getEmail() + ")");

        emailServices.sendAccountBannedEmail(userEmail);

        return userRepository.save(user);
    }

    public User unbanUser(Long id) {
        User user = getUserById(id);

        user.setBanned(false);
        auditLogService.log(
                AuditLog.AuditAction.USER_BANNED, currentUserService.getCurrentUser(),
                "Unbanned user id=" + user.getId() + " (" + user.getEmail() + ")");

        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        User user = getUserById(id);

        userRepository.delete(user);
        auditLogService.log(
                AuditLog.AuditAction.USER_BANNED, currentUserService.getCurrentUser(),
                "Deleted user id=" + user.getId() + " (" + user.getEmail() + ")");
    }
}
