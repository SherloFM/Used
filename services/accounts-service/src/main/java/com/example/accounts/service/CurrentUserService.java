package com.example.accounts.service;

import com.example.accounts.model.User;
import com.example.accounts.repository.UserRepository;
import com.example.accounts.security.MyUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        MyUserDetails myUserDetails = (MyUserDetails) authentication.getPrincipal();

        return myUserDetails.getUser();
    }
}
