package com.example.accounts.model.requests;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserProfileRequests {
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String profileBio;
}
