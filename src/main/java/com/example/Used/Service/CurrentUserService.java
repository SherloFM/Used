package com.example.Used.Service;

import com.example.Used.Model.User;
import com.example.Used.Repository.UserRepository;
import com.example.Used.Security.MyUserDetails;
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
