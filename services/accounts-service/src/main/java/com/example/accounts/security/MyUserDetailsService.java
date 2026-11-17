package com.example.accounts.security;

import com.example.accounts.model.User;
import com.example.accounts.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MyUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public MyUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = users.findByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException("Account not found");
        }
        return new MyUserDetails(user);
    }

    public MyUserDetails loadUserById(Long id) {
        return users.findById(id).map(MyUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }
}
