package com.example.accounts.model.responses;

import com.example.accounts.model.User;
import com.example.accounts.model.UserProfile;

import java.time.LocalDateTime;

/**
 * Account and profile information returned by Accounts.
 * Wallet balances are retrieved separately from Marketplace.
 */
public class ProfileResponse {

    public Long id;
    public String username;
    public String email;
    public String firstName;
    public String lastName;
    public String phoneNumber;
    public String bio;
    public String imgUrl;
    public byte[] img;
    public String imgtype;
    public LocalDateTime createdAt;
    /** "ADMIN" or "USER" — lets the frontend guard admin pages. */
    public String role;

    public static ProfileResponse from(User user, UserProfile profile) {
        ProfileResponse response = new ProfileResponse();
        response.id = user.getId();
        response.username = user.getUsername();
        response.email = user.getEmail();
        response.createdAt = user.getCreatedAt();
        response.role = user.getRole() != null ? user.getRole().name() : null;

        if (profile != null) {
            response.firstName = profile.getFirstName();
            response.lastName = profile.getLastName();
            response.phoneNumber = profile.getPhoneNumber();
            response.bio = profile.getProfileBio();
            response.imgUrl = profile.getImgUrl();
            response.img = profile.getImg();
            response.imgtype = profile.getImgtype();
        }
        return response;
    }
}
