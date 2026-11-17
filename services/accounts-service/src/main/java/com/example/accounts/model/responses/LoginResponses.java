package com.example.accounts.model.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponses {
    private String message;
    private String role;
    @JsonIgnore
    private String token;

    /** Browser-visible error message. */
    public LoginResponses(String message) {
        this.message = message;
    }

    /** Token is used only by the controller to set the HttpOnly cookie. */
    public LoginResponses(String token, String role) {
        this.message = "Signed in";
        this.token = token;
        this.role = role;
    }
}
