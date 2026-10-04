package com.example.Used.Service;

import com.example.Used.Model.User;
import com.example.Used.Repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
    }

    public User banUser(Long id) {
        User user = getUserById(id);

        user.setBanned(true);

        return userRepository.save(user);
    }

    public User unbanUser(Long id) {
        User user = getUserById(id);

        user.setBanned(false);

        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        User user = getUserById(id);

        userRepository.delete(user);
    }
}
