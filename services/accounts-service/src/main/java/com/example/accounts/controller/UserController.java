package com.example.accounts.controller;

import com.example.accounts.model.requests.LoginRequests;
import com.example.accounts.model.requests.VerificationRequests;
import com.example.accounts.model.requests.passwordmanager.ChangePasswordRequests;
import com.example.accounts.model.requests.passwordmanager.ForgetPasswordRequests;
import com.example.accounts.model.requests.passwordmanager.ResetPasswordRequests;
import com.example.accounts.model.User;
import com.example.accounts.service.UserService;
import com.example.accounts.model.responses.LoginResponses;
import com.example.accounts.security.AuthCookieService;
import com.example.accounts.security.MyUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@AllArgsConstructor
@RestController
@RequestMapping(path = "/auth/user")
public class UserController {

    private UserService userService;
    private AuthCookieService authCookies;
    private CookieCsrfTokenRepository csrfTokens;

    @PostMapping("/register")
    public User register(
            @RequestBody User userObject
    ){
        return userService.createUser(userObject);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyEmail(
            @RequestBody VerificationRequests request
    ){
        return userService.verifyEmail(request);
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(
            @Valid @RequestBody LoginRequests loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ){
        ResponseEntity<?> result = userService.loginUser(loginRequest);
        if (result.getStatusCode().is2xxSuccessful()
                && result.getBody() instanceof LoginResponses login
                && login.getToken() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, authCookies.issue(login.getToken()).toString());
            csrfTokens.saveToken(null, request, response);
            // Return a token-free object, even apart from @JsonIgnore on the internal result.
            return ResponseEntity.ok(Map.of("message", "Signed in", "role", login.getRole()));
        }
        return result;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(@AuthenticationPrincipal MyUserDetails principal) {
        User user = principal.getUser();
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("id", user.getId(), "username", user.getUsername(),
                        "role", user.getRole().name()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        csrfTokens.saveToken(null, request, response);
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookies.clear().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PostMapping("/forget-password")
    public ResponseEntity<?> forgotPassword(
            @Valid @RequestBody ForgetPasswordRequests request
    ) {
        return userService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody ResetPasswordRequests request
    ) {
        return userService.resetPassword(request);
    }

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequests request
    ) {
        return userService.changePassword(request);
    }

    @PostMapping("/resend-verify")
    public ResponseEntity<?> resendVerification(
            @RequestBody Map<String, String> request // Simple map: {"email": "..."}
    ) {
        String email = request.get("email");
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body("Email is required");
        }
        return userService.resendVerificationCode(email);
    }

}
