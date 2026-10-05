package com.example.Used.Tests;

import com.example.Used.Repository.UserRepository;
import com.example.Used.Security.JWTUtilities;
import com.example.Used.Security.LoginRateLimiter;
import com.example.Used.Security.MyUserDetails;
import com.example.Used.Service.CurrentUserService;
import com.example.Used.Service.EmailServices;
import com.example.Used.Service.UserService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private MyUserDetails myUserDetails;

    @Mock
    private JWTUtilities jwtUtilities;

    @Mock
    private EmailServices emailServices;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserService userService;
}

