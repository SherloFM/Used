package com.example.accounts.service;

import com.example.accounts.exceptions.InformationExistException;
import com.example.accounts.exceptions.RateLimitException;
import com.example.accounts.client.AuditClient;
import com.example.accounts.client.EmailClient;
import com.example.accounts.model.requests.LoginRequests;
import com.example.accounts.model.requests.passwordmanager.ChangePasswordRequests;
import com.example.accounts.model.requests.passwordmanager.ForgetPasswordRequests;
import com.example.accounts.model.requests.passwordmanager.ResetPasswordRequests;
import com.example.accounts.model.responses.LoginResponses;
import com.example.accounts.model.User;
import com.example.accounts.model.UserProfile;
import com.example.accounts.repository.UserRepository;
import com.example.accounts.security.JWTUtilities;
import com.example.accounts.security.LoginRateLimiter;
import com.example.accounts.security.MyUserDetails;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.accounts.model.requests.VerificationRequests;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private MyUserDetails myUserDetails;
    private final JWTUtilities jwtUtilities;
    private final EmailClient emailClient;
    private final LoginRateLimiter loginRateLimiter;
    private final CurrentUserService currentUserService;
    private final AuditClient auditClient;
    private final ConcurrentHashMap<String, Long> lastResendTime = new ConcurrentHashMap<>();

    @Value("${verification.resend.cooldown-seconds:60}")
    private int cooldownSeconds;

    @Autowired
    public UserService(
            UserRepository userRepository,
            @Lazy PasswordEncoder passwordEncoder,
            @Lazy AuthenticationManager authenticationManager,
            MyUserDetails myUserDetails,
            JWTUtilities jwtUtilities,
            EmailClient emailClient,
            LoginRateLimiter loginRateLimiter,
                    CurrentUserService currentUserService,
            AuditClient auditClient

    ){
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder =passwordEncoder;
        this.myUserDetails = myUserDetails;
        this.jwtUtilities = jwtUtilities;
        this.emailClient = emailClient;
        this.loginRateLimiter = loginRateLimiter;
        this.currentUserService = currentUserService;
        this.auditClient = auditClient;
    }



    private static User getCurrentLoggedInUser(){
        MyUserDetails userDetails = (MyUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return userDetails.getUser();
    }

    public User createUser(User userObject){
        String normalizedEmail = userObject.getEmail() == null
                ? "" : userObject.getEmail().trim().toLowerCase();
        String rawPassword = userObject.getPassword();
        if (normalizedEmail.isBlank() || rawPassword == null || rawPassword.length() < 6) {
            throw new InformationExistException("A valid email and password of at least 6 characters are required");
        }
        if (!userRepository.existsByEmail(normalizedEmail)){
            userObject.setEmail(normalizedEmail);

            UserProfile userProfile = new UserProfile();
            userProfile.setUser(userObject);
            userObject.setUserProfile(userProfile);
            userObject.setRole(User.Role.USER);
            // Encode the browser's raw password exactly once. Encoding the first
            // BCrypt hash a second time made every newly registered password fail.
            userObject.setPassword(passwordEncoder.encode(rawPassword));
            userObject.setEmailVerified(false);
            String emailVerificationCode = String.format("%06d",new Random().nextInt(1000000));
            userObject.setVerificationCode(emailVerificationCode);
            userObject.setVerificationCodeExpiration(LocalDateTime.now().plusMinutes(10));

            User savedUser = userRepository.save(userObject);

            lastResendTime.put(savedUser.getEmail(), Instant.now().getEpochSecond());

            auditClient.log(
                    "USER_REGISTERED",
                    savedUser.getId(),
                    savedUser.getUsername(),
                    "Registered new user " + savedUser.getEmail()
            );


            emailClient.sendVerificationEmail(
                    savedUser.getEmail(),
                    emailVerificationCode
            );



            return savedUser;
        }else{
            throw new InformationExistException("already exists");
        }
    }

    public ResponseEntity<?> resendVerificationCode(String email) {

        // 1. Normalize email
        String normalizedEmail = email.toLowerCase().trim();

        // 2. Check Rate Limit
        Long lastTime = lastResendTime.get(normalizedEmail);
        long currentTime = Instant.now().getEpochSecond();

        if (lastTime != null && (currentTime - lastTime) < cooldownSeconds) {
            long remaining = cooldownSeconds - (currentTime - lastTime);
            throw new RateLimitException("Please wait " + remaining + " seconds before requesting a new code.");
        }

        // 3. Find User
        User user = userRepository.findByEmail(normalizedEmail);
        if (user == null) {
            // Generic message to avoid leaking existence of account
            return ResponseEntity.ok("If the email exists, a new code has been sent.");
        }

        // 4. Generate New Code & Expiry
        String newCode = String.format("%06d", new Random().nextInt(1000000));
        user.setVerificationCode(newCode);
        user.setVerificationCodeExpiration(LocalDateTime.now().plusMinutes(10));

        userRepository.save(user);

        // 5. Update Last Sent Time
        lastResendTime.put(normalizedEmail, currentTime);

        // 6. Send Email
        try {
            emailClient.sendVerificationEmail(normalizedEmail, newCode);
        } catch (Exception e) {
            // Log error internally but return generic success to user
            System.err.println("Failed to send verification email: " + e.getMessage());
        }

        // 7. Audit Log
        auditClient.log(
                "EMAIL_VERIFIED",
                user.getId(),
                user.getUsername(),
                "Resent verification code to " + normalizedEmail
        );

        return ResponseEntity.ok("A new verification code has been sent to your email.");
    }


    public User findByEmailAddress(String email){
        return userRepository.findByEmail(email);
    }

    public ResponseEntity<?> loginUser(LoginRequests loginRequest){
        String email = loginRequest.getEmail().toLowerCase();

        if (!loginRateLimiter.isAllowed(email)) {
            return ResponseEntity
                    .status(429)
                    .body(new LoginResponses("Too many login attempts"));
        }

        User user = userRepository.findByEmail(email);

        if (user == null){
            loginRateLimiter.recordFailedAttempt(email);

            return ResponseEntity
                    .status(401)
                    .body(new LoginResponses("Invalid email or password"));

        }
        if(user.isBanned()){
            return ResponseEntity
                    .status(403)
                    .body(new LoginResponses("Account is banned"));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            loginRequest.getPassword()
                    )
            );

            loginRateLimiter.resetAttempts(email);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            MyUserDetails authenticatedUser = (MyUserDetails) authentication.getPrincipal();
            final String JWT = jwtUtilities.generateJWTtoken(authenticatedUser);
            return ResponseEntity.ok(new LoginResponses(JWT, user.getRole().name()));
        } catch (Exception e) {
            loginRateLimiter.recordFailedAttempt(email);
            return ResponseEntity
                    .status(401)
                    .body(new LoginResponses("Invalid email or password"));
        }
    }

    public ResponseEntity<?> verifyEmail(VerificationRequests request){

        User user = userRepository.findByEmail(request.getEmail());

        if (user == null) {
            return ResponseEntity.badRequest().body("User not found");
        }

        if (user.isEmailVerified()) {
            return ResponseEntity.badRequest().body("Email is already verified");
        }

        if (user.getVerificationCodeExpiration() == null ||
                LocalDateTime.now().isAfter(user.getVerificationCodeExpiration())) {

            return ResponseEntity.badRequest().body("Verification code expired");
        }

        if (!user.getVerificationCode().equals(request.getCode())) {
            return ResponseEntity.badRequest().body("Invalid verification code");
        }

        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiration(null);

        userRepository.save(user);

        auditClient.log(
                "EMAIL_VERIFIED",
                user.getId(),
                user.getUsername(),
                "Email verified for " + user.getEmail(),
                user.getId()
        );

        // Verification completes registration and signs the user in without
        // asking for the password again. We deliberately do not issue a JWT
        // before verification: downstream services trust any JWT they receive.
        String jwt = jwtUtilities.generateJWTtoken(new MyUserDetails(user));
        return ResponseEntity.ok(new LoginResponses(jwt, user.getRole().name()));
    }

    public ResponseEntity<?> forgotPassword(ForgetPasswordRequests request) {

        User user = userRepository.findByEmail(request.getEmail());

        if (user == null) {
            return ResponseEntity.ok(
                    "If the email exists, a password reset code has been sent"
            );
        }

        String resetCode = String.format("%06d", new Random().nextInt(1000000));

        user.setPasswordResetCode(resetCode);
        user.setPasswordResetCodeExpiration(LocalDateTime.now().plusMinutes(10));

        userRepository.save(user);

        emailClient.sendPasswordResetEmail(
                user.getEmail(),
                resetCode
        );

        return ResponseEntity.ok(
                "If the email exists, a password reset code has been sent"
        );
    }

    public ResponseEntity<?> resetPassword(
            ResetPasswordRequests request
    ) {

        User user = userRepository.findByEmail(request.getEmail());

        //user null check
        if (user == null) {
            return ResponseEntity.badRequest().body("Invalid reset request");
        }

        //reset code null check
        if (user.getPasswordResetCode() == null || user.getPasswordResetCodeExpiration() == null) {
            return ResponseEntity.badRequest().body("Invalid or expired reset code");
        }

        //check datet time if before expiration
        if (LocalDateTime.now().isAfter(user.getPasswordResetCodeExpiration())) {
            return ResponseEntity.badRequest().body("Invalid or expired reset code");
        }

        //reset code validity check
        if (!user.getPasswordResetCode().equals(request.getCode())) {
            return ResponseEntity.badRequest().body("Invalid or expired reset code");
        }

        //change password
        user.setPassword(
                passwordEncoder.encode(request.getNewPassword())
        );

        user.setPasswordResetCode(null);
        user.setPasswordResetCodeExpiration(null);

        userRepository.save(user);

        auditClient.log(
                "PASSWORD_RESET",
                user.getId(),
                user.getUsername(),
                "Password reset for " + user.getEmail(),
                user.getId()
        );

        return ResponseEntity.ok(
                "Password reset successfully"
        );
    }

    public ResponseEntity<?> changePassword(ChangePasswordRequests request) {

        //store current user in user object
        User user = currentUserService.getCurrentUser();

        //check if password is correct
        if (!passwordEncoder.matches(
                request.getCurrentPassword(),
                user.getPassword()
        )) {
            return ResponseEntity.badRequest()
                    .body("Current password is incorrect");
        }


        //check if new matches old
        if (passwordEncoder.matches(
                request.getNewPassword(),
                user.getPassword()
        )) {
            return ResponseEntity.badRequest()
                    .body("New password must be different from current password");
        }

        //set new pasword and save
        user.setPassword(
                passwordEncoder.encode(request.getNewPassword())
        );

        userRepository.save(user);

        auditClient.log(
                "PASSWORD_CHANGED",
                user.getId(),
                user.getUsername(),
                "Password changed for " + user.getEmail(),
                user.getId()
        );


        return ResponseEntity.ok(
                "Password changed successfully"
        );
    }

}
