package com.example.accounts.service;

import com.example.accounts.client.AuditClient;
import com.example.accounts.client.EmailClient;
import com.example.accounts.model.User;
import com.example.accounts.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AuditClient auditClient;
    private final CurrentUserService currentUserService;
    private final EmailClient emailClient;

    public AdminService(
            UserRepository userRepository,
            AuditClient auditClient,
            CurrentUserService currentUserService,
            EmailClient emailClient
    ) {

        this.auditClient = auditClient;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.emailClient  = emailClient;
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
        User actor = currentUserService.getCurrentUser();
        auditClient.log(
                "USER_BANNED",
                actor.getId(),
                actor.getUsername(),
                "Banned user id=" + user.getId() + " (" + user.getEmail() + ")",
                user.getId()
        );

        emailClient.sendAccountBannedEmail(userEmail);

        return userRepository.save(user);
    }

    public User unbanUser(Long id) {
        User user = getUserById(id);
        String userEmail = user.getEmail();

        user.setBanned(false);
        User actor = currentUserService.getCurrentUser();
        auditClient.log(
                "USER_BANNED",
                actor.getId(),
                actor.getUsername(),
                "Unbanned user id=" + user.getId() + " (" + user.getEmail() + ")",
                user.getId()
        );

        emailClient.sendAccountUnbannedEmail(userEmail); // then notify

        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        User user = getUserById(id);
        String userEmail = user.getEmail();
        Long victimId = user.getId();          // capture before the row is gone

        userRepository.delete(user);
        User actor = currentUserService.getCurrentUser();
        auditClient.log(
                "USER_BANNED",
                actor.getId(),
                actor.getUsername(),
                "Deleted user id=" + victimId + " (" + userEmail + ")",
                victimId                       // targeted: the VICTIM
        );
        emailClient.sendAccountDeletedEmail(userEmail);

    }
}
