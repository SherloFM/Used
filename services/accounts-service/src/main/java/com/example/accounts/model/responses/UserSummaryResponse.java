package com.example.accounts.model.responses;

import com.example.accounts.model.User;

/**
 * The only user data other services may read. Passwords, verification codes,
 * reset codes and profile details are deliberately not part of this contract.
 */
public record UserSummaryResponse(Long id, String username, String email, String role) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole() == null ? null : user.getRole().name());
    }
}
