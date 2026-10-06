package com.example.Used.Repository;

import com.example.Used.Model.Categories;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Categories, Long> {
    Optional<Categories> findByNameIgnoreCase(String name);
}
