package com.example.accounts.service;

import com.example.accounts.client.MediaClient;
import com.example.accounts.client.MediaUploadResponse;
import com.example.accounts.exceptions.InformationExistException;
import com.example.accounts.model.User;
import com.example.accounts.model.UserProfile;
import com.example.accounts.repository.UserProfileRepository;
import com.example.accounts.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserProfileServiceTest {

    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final UserProfileRepository profiles = mock(UserProfileRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MediaClient media = mock(MediaClient.class);
    private UserProfileService service;
    private UserProfile profile;

    @BeforeEach
    void setup() {
        profile = new UserProfile();
        profile.setImg(new byte[]{9, 9});
        profile.setImgtype("image/jpeg");
        profile.setImgUrl("/old/image");
        User user = new User();
        user.setId(7L);
        user.setUserProfile(profile);
        when(currentUser.getCurrentUser()).thenReturn(user);
        service = new UserProfileService(users, profiles, currentUser, media);
    }

    @Test
    void storesUrlAndClearsInlineBytesOnlyAfterMediaSucceeds() throws IOException {
        when(media.upload(eq(7L), any())).thenReturn(new MediaUploadResponse(
                UUID.randomUUID(), "/api/media/x", "http://localhost:8088/api/media/x",
                "image/png", 3, "abc"));

        service.uploadProfileImage(image());

        assertEquals("http://localhost:8088/api/media/x", profile.getImgUrl());
        assertNull(profile.getImg());
        assertNull(profile.getImgtype());
        verify(profiles).save(profile);
    }

    @Test
    void mediaFailureLeavesThePreviousImageUntouched() throws IOException {
        when(media.upload(eq(7L), any())).thenThrow(new IllegalStateException("Media unavailable"));

        assertThrows(IllegalStateException.class, () -> service.uploadProfileImage(image()));

        assertArrayEquals(new byte[]{9, 9}, profile.getImg());
        assertEquals("/old/image", profile.getImgUrl());
        verify(profiles, never()).save(any());
    }

    @Test
    void rejectsNonImagesBeforeCallingMedia() {
        var file = new MockMultipartFile("img", "notes.txt", "text/plain", "hello".getBytes());
        assertThrows(InformationExistException.class, () -> service.uploadProfileImage(file));
        verifyNoInteractions(media);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("img", "avatar.png", "image/png", new byte[]{1, 2, 3});
    }
}
