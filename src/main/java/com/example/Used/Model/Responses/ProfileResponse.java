package com.example.Used.Model.Responses;

import com.example.Used.Model.User;
import com.example.Used.Model.UserProfile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What the profile page needs in one call: account data (email, username),
 * profile data and the wallet balance. Returning the raw UserProfile was not
 * enough because it has no email, and it is null for users who never edited
 * their profile.
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
    public BigDecimal walletBalance;
    public LocalDateTime createdAt;
    /** "ADMIN" or "USER" — lets the frontend guard admin pages. */
    public String role;

    public static ProfileResponse from(User user, UserProfile profile, BigDecimal walletBalance) {
        ProfileResponse response = new ProfileResponse();
        response.id = user.getId();
        response.username = user.getUsername();
        response.email = user.getEmail();
        response.walletBalance = walletBalance;
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
