package com.example.Used.Model.Responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponses {
    private String message;
    private String role;

    /** Existing shape: error messages and the old token-only response. */
    public LoginResponses(String message) {
        this.message = message;
    }

    /** Success: token + role so the frontend can route admins to the portal. */
    public LoginResponses(String token, String role) {
        this.message = token;
        this.role = role;
    }
}
