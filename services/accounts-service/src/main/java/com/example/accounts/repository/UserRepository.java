package com.example.accounts.repository;

import com.example.accounts.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String emailAddress);
    User findByEmail(String emailAddress);
}