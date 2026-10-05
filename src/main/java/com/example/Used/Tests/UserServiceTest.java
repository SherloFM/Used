package com.example.Used.Tests;

import com.example.Used.Model.Requests.LoginRequests;
import com.example.Used.Model.Responses.LoginResponses;
import com.example.Used.Model.User;
import com.example.Used.Repository.UserRepository;
import com.example.Used.Security.JWTUtilities;
import com.example.Used.Security.LoginRateLimiter;
import com.example.Used.Security.MyUserDetails;
import com.example.Used.Service.CurrentUserService;
import com.example.Used.Service.EmailServices;
import com.example.Used.Service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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

    @Test
    void loginUser_validCredentials_returnsJwt() {

        LoginRequests request = new LoginRequests();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User user = new User();
        user.setEmail("test@example.com");
        user.setBanned(false);

        when(loginRateLimiter.isAllowed("test@example.com"))
                .thenReturn(true);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(user);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        when(authentication.getPrincipal())
                .thenReturn(myUserDetails);

        when(jwtUtilities.generateJWTtoken(myUserDetails))
                .thenReturn("test-jwt-token");

        ResponseEntity<?> response =
                userService.loginUser(request);

        assertEquals(200, response.getStatusCode().value());

        LoginResponses body =
                (LoginResponses) response.getBody();

        assertEquals(
                "test-jwt-token",
                body.getMessage()
        );

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        verify(loginRateLimiter)
                .resetAttempts("test@example.com");
    }

    @Test
    void loginUser_wrongPassword_returnsUnauthorized() {

        LoginRequests request = new LoginRequests();
        request.setEmail("test@example.com");
        request.setPassword("wrongPassword");

        User user = new User();
        user.setEmail("test@example.com");
        user.setBanned(false);

        when(loginRateLimiter.isAllowed("test@example.com"))
                .thenReturn(true);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(user);

        when(authenticationManager.authenticate(any(
                UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new RuntimeException());

        ResponseEntity<?> response =
                userService.loginUser(request);

        assertEquals(401, response.getStatusCode().value());

        LoginResponses body =
                (LoginResponses) response.getBody();

        assertEquals(
                "Invalid email or password",
                body.getMessage()
        );

        verify(loginRateLimiter)
                .recordFailedAttempt("test@example.com");
    }
}

