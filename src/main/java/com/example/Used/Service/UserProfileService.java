package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Model.Requests.UserProfileRequests;
import com.example.Used.Model.User;
import com.example.Used.Model.UserProfile;
import com.example.Used.Repository.UserProfileRepository;
import com.example.Used.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

@Service
public class UserProfileService {

    private final CurrentUserService currentUserService;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;

    @Autowired
    public UserProfileService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            CurrentUserService currentUserService
    ) {
        this.userProfileRepository = userProfileRepository;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
    }

    public UserProfile getProfile(){
        User user = currentUserService.getCurrentUser();

        return user.getUserProfile();
    }

    public UserProfile updateProfile(
            UserProfileRequests userProfileRequests
    ){
        User user = currentUserService.getCurrentUser();

        UserProfile userProfileObject = user.getUserProfile();


        userProfileObject.setFirstName(userProfileRequests.getFirstName());
        userProfileObject.setLastName(userProfileRequests.getLastName());
        userProfileObject.setPhoneNumber(userProfileRequests.getPhoneNumber());
        userProfileObject.setProfileBio(userProfileRequests.getProfileBio());

        return userProfileRepository.save(userProfileObject);
    }

    public UserProfile uploadProfileImage(MultipartFile imgFile) throws IOException {
        // 1. Get current logged-in user
        User currentUser = currentUserService.getCurrentUser();

        // 2. Find their profile
        UserProfile profile = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getUserProfile();

        if (profile == null) {
            throw new RuntimeException("Profile not initialized");
        }

        // 3. Validate file size/type (Optional but recommended)
        if (imgFile.getSize() > 5 * 1024 * 1024) { // 5MB limit
            throw new IllegalArgumentException("Image too large. Max 5MB.");
        }
        String contentType = imgFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Invalid file type. Must be an image.");
        }

        // 4. Convert to byte array and save
        byte[] imageBytes = imgFile.getBytes();
        profile.setImg(imageBytes);       // Assuming field name is 'img'
        profile.setImgtype(contentType);  // Assuming field name is 'imgtype'

        userProfileRepository.save(profile);

        return profile;
    }
}
