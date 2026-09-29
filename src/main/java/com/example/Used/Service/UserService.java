package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Model.User;
import com.example.Used.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;


    @Autowired
    public UserService(
            UserRepository userRepository,
            @Lazy PasswordEncoder passwordEncoder,
            @Lazy AuthenticationManager authenticationManager
    ){
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder =passwordEncoder;
    }

    public User createUser(User userObject){
        if (!userRepository.existsByEmail(userObject.getEmail())){
            userObject.setPassword(passwordEncoder.encode(userObject.getPassword()));
            return userRepository.save(userObject);
        }else{
            throw new InformationExistException("already exists");
        }
    }

    public User findByEmailAddress(String email){
        return userRepository.findByEmail(email);
    }

//    public ResponseEntity<?> loginUser(LoginRequest loginRequest){
//        try {
//            Authentication authentication = authenticationManager.authenticate(
//                    new UsernamePasswordAuthenticationToken(
//                            loginRequest.getEmail(),
//                            loginRequest.getPassword()
//                    ));
//            SecurityContextHolder.getContext().setAuthentication(authentication);
//            myUserDetails = (MyUserDetails) authentication.getPrincipal();
//            final String JWT = jwtUtils.generateJWTtoken(myUserDetails);
//            return ResponseEntity.ok(new LoginResponse(JWT));
//        }catch (Exception e){
//            return ResponseEntity.ok(new LoginResponse("login failed"));
//        }
//    }
}
