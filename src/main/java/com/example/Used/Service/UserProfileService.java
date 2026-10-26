package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Model.Requests.UserProfileRequests;
import com.example.Used.Model.Responses.ProfileResponse;
import com.example.Used.Model.User;
import com.example.Used.Model.UserProfile;
import com.example.Used.Model.Wallet;
import com.example.Used.Repository.UserProfileRepository;
import com.example.Used.Repository.UserRepository;
import com.example.Used.Repository.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;

@Service
public class UserProfileService {

    private final CurrentUserService currentUserService;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    @Autowired
    public UserProfileService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            WalletRepository walletRepository,
            CurrentUserService currentUserService
    ) {
        this.userProfileRepository = userProfileRepository;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
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

        profile.setImg(imgFile.getBytes());
        profile.setImgtype(contentType);

        userProfileRepository.save(profile);
        if (created) {
            userRepository.save(user);
        }

        return toResponse(user);
    }

    private ProfileResponse toResponse(User user) {
        BigDecimal balance = walletRepository.findByUserId(user.getId())
                .map(Wallet::getBalance)
                .orElse(BigDecimal.ZERO);
        return ProfileResponse.from(user, user.getUserProfile(), balance);
    }
}
