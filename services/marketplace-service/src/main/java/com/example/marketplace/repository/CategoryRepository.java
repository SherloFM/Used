package com.example.marketplace.repository;

import com.example.marketplace.model.Categories;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Categories, Long> {
    Optional<Categories> findByNameIgnoreCase(String name);
}
