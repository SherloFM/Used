package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Model.AuditLog;
import com.example.Used.Model.Requests.LoginRequests;
import com.example.Used.Model.Requests.passwordManager.ChangePasswordRequests;
import com.example.Used.Model.Requests.passwordManager.ForgetPasswordRequests;
import com.example.Used.Model.Requests.passwordManager.ResetPasswordRequests;
import com.example.Used.Model.Responses.LoginResponses;
import com.example.Used.Model.User;
import com.example.Used.Model.UserProfile;
import com.example.Used.Repository.UserRepository;
import com.example.Used.Security.JWTUtilities;
import com.example.Used.Security.LoginRateLimiter;
import com.example.Used.Security.MyUserDetails;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.Used.Model.Requests.VerificationRequests;
import java.time.LocalDateTime;
import java.util.Random;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private MyUserDetails myUserDetails;
    private final JWTUtilities jwtUtilities;
    private final EmailServices emailServices;
    private final LoginRateLimiter loginRateLimiter;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @Autowired
    public UserService(
            UserRepository userRepository,
            @Lazy PasswordEncoder passwordEncoder,
            @Lazy AuthenticationManager authenticationManager,
            MyUserDetails myUserDetails,
            JWTUtilities jwtUtilities,
            EmailServices emailServices,
            LoginRateLimiter loginRateLimiter,
                    CurrentUserService currentUserService,
            AuditLogService auditLogService

    ){
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder =passwordEncoder;
        this.myUserDetails = myUserDetails;
        this.jwtUtilities = jwtUtilities;
        this.emailServices = emailServices;
        this.loginRateLimiter = loginRateLimiter;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }


    private static User getCurrentLoggedInUser(){
        MyUserDetails userDetails = (MyUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return userDetails.getUser();
    }

    public User createUser(User userObject){
        if (!userRepository.existsByEmail(userObject.getEmail())){
            userObject.setPassword(passwordEncoder.encode(userObject.getPassword()));

            UserProfile userProfile = new UserProfile();
            userProfile.setUser(userObject);
            userObject.setUserProfile(userProfile);
            userObject.setRole(User.Role.USER);
            userObject.setPassword(passwordEncoder.encode(userObject.getPassword()));
            userObject.setEmailVerified(false);
            String emailVerificationCode = String.format("%06d",new Random().nextInt(1000000));
            userObject.setVerificationCode(emailVerificationCode);
            userObject.setVerificationCodeExpiration(LocalDateTime.now().plusMinutes(10));

            User savedUser = userRepository.save(userObject);

            auditLogService.log(
                    AuditLog.AuditAction.USER_REGISTERED,
                    savedUser,
                    "Registered new user " + savedUser.getEmail()
            );


            emailServices.sendVerificationEmail(
                    savedUser.getEmail(),
                    emailVerificationCode
            );



            return savedUser;
        }else{
            throw new InformationExistException("already exists");
        }
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
            myUserDetails = (MyUserDetails) authentication.getPrincipal();
            final String JWT = jwtUtilities.generateJWTtoken(myUserDetails);
            return ResponseEntity.ok(new LoginResponses(JWT));
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

        auditLogService.log(
                AuditLog.AuditAction.EMAIL_VERIFIED,
                user,
                "Email verified for " + user.getEmail()
        );

        return ResponseEntity.ok("Email verified successfully");
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

        emailServices.sendPasswordResetEmail(
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

        auditLogService.log(
                AuditLog.AuditAction.PASSWORD_RESET,
                user,
                "Password reset for " + user.getEmail()
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

        return ResponseEntity.ok(
                "Password changed successfully"
        );
    }

}
