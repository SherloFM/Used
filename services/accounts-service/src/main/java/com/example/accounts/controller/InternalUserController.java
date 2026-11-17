package com.example.accounts.controller;

import com.example.accounts.model.responses.UserSummaryResponse;
import com.example.accounts.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service user lookup. Reached only with X-Internal-Token;
 * ROLE_INTERNAL is granted by InternalTokenFilter, never by a browser cookie.
 */
@AllArgsConstructor
@RestController
@RequestMapping("api/internal/users")
public class InternalUserController {

    private final UserRepository userRepository;

    @GetMapping("/{id}")
    public ResponseEntity<UserSummaryResponse> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(UserSummaryResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
