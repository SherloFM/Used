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

    @Autowired
    public UserProfileService(
            UserProfileRepository userProfileRepository,
            CurrentUserService currentUserService
    ) {
        this.userProfileRepository = userProfileRepository;
        this.currentUserService = currentUserService;
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

    public UserProfile uploadImage(MultipartFile img) throws IOException {

        if (img.isEmpty()) {
            throw new IllegalArgumentException("Image is required");
        }

        if (img.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Image must be smaller than 5 MB");
        }


        String contentType = img.getContentType();
        if (!"image/jpeg".equals(contentType) &&
                !"image/png".equals(contentType)) {
            throw new IllegalArgumentException(
                    "Only JPG, JPEG and PNG images are allowed"
            );
        }

        // Check image dimensions
        java.awt.image.BufferedImage image =
                javax.imageio.ImageIO.read(img.getInputStream());

        if (image == null) {
            throw new IllegalArgumentException("Invalid image file");
        }

        if (image.getWidth() > 4000 || image.getHeight() > 4000) {
            throw new IllegalArgumentException(
                    "Image dimensions must not exceed 4000x4000 pixels"
            );
        }

        User user = currentUserService.getCurrentUser();

        UserProfile userProfileObject = user.getUserProfile();

        userProfileObject.setImg(img.getBytes());
        userProfileObject.setImgtype(contentType);

        return userProfileRepository.save(userProfileObject);
    }
}
