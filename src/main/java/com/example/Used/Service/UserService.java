package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Model.Requests.LoginRequests;
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


    @Autowired
    public UserService(
            UserRepository userRepository,
            @Lazy PasswordEncoder passwordEncoder,
            @Lazy AuthenticationManager authenticationManager,
            MyUserDetails myUserDetails,
            JWTUtilities jwtUtilities,
            EmailServices emailServices,
            LoginRateLimiter loginRateLimiter
    ){
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder =passwordEncoder;
        this.myUserDetails = myUserDetails;
        this.jwtUtilities = jwtUtilities;
        this.emailServices = emailServices;
        this.loginRateLimiter = loginRateLimiter;
    }

    public User createUser(User userObject){
        if (!userRepository.existsByEmail(userObject.getEmail())){
            userObject.setPassword(passwordEncoder.encode(userObject.getPassword()));

            UserProfile userProfile = new UserProfile();
            userProfile.setUser(userObject);
            userObject.setUserProfile(userProfile);

            userObject.setEmailVerified(false);
            String emailVerificationCode = String.format("%06d",new Random().nextInt());
            userObject.setVerificationCode(emailVerificationCode);
            userObject.setVerificationCodeExpiration(LocalDateTime.now().plusMinutes(10));

            User savedUser = userRepository.save(userObject);

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

        return ResponseEntity.ok("Email verified successfully");
    }

}
