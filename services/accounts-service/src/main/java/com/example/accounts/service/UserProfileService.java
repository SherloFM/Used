package com.example.accounts.service;

import com.example.accounts.exceptions.InformationExistException;
import com.example.accounts.client.MediaClient;
import com.example.accounts.client.MediaUploadResponse;
import com.example.accounts.model.requests.UserProfileRequests;
import com.example.accounts.model.responses.ProfileResponse;
import com.example.accounts.model.User;
import com.example.accounts.model.UserProfile;
import com.example.accounts.repository.UserProfileRepository;
import com.example.accounts.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class UserProfileService {

    private final CurrentUserService currentUserService;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final MediaClient mediaClient;

    @Autowired
    public UserProfileService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            CurrentUserService currentUserService,
            MediaClient mediaClient
    ) {
        this.userProfileRepository = userProfileRepository;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.mediaClient = mediaClient;
    }

    public ProfileResponse getProfile() {
        return toResponse(currentUserService.getCurrentUser());
    }

    public ProfileResponse updateProfile(UserProfileRequests userProfileRequests) {
        User user = currentUserService.getCurrentUser();
        UserProfile profile = user.getUserProfile();
        boolean created = false;

        // Some accounts (e.g. seeded buyers, admin) never had a profile row.
        if (profile == null) {
            profile = new UserProfile();
            user.setUserProfile(profile);
            created = true;
        }

        profile.setFirstName(userProfileRequests.getFirstName());
        profile.setLastName(userProfileRequests.getLastName());
        profile.setPhoneNumber(userProfileRequests.getPhoneNumber());
        profile.setProfileBio(userProfileRequests.getProfileBio());

        userProfileRepository.save(profile);
        if (created) {
            userRepository.save(user); // persist the new profile link on users
        }

        return toResponse(user);
    }

    public ProfileResponse uploadProfileImage(MultipartFile imgFile) throws IOException {
        User user = currentUserService.getCurrentUser();
        UserProfile profile = user.getUserProfile();
        boolean created = false;

        if (profile == null) {
            profile = new UserProfile();
            user.setUserProfile(profile);
            created = true;
        }

        if (imgFile.getSize() > 5 * 1024 * 1024) {
            throw new InformationExistException("Image is too large. Maximum size is 5 MB.");
        }
        String contentType = imgFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InformationExistException("Please choose an image file.");
        }

        // Store first. If Media rejects the image or is unreachable, the existing
        // profile row is untouched. Bytes are cleared only after the new URL is known.
        MediaUploadResponse uploaded = mediaClient.upload(user.getId(), imgFile);
        // Relative path: the avatar must resolve on every host that serves the site.
        profile.setImgUrl(uploaded.path());
        profile.setImg(null);
        profile.setImgtype(null);

        userProfileRepository.save(profile);
        if (created) {
            userRepository.save(user);
        }

        return toResponse(user);
    }

    private ProfileResponse toResponse(User user) {
        return ProfileResponse.from(user, user.getUserProfile());
    }
}
