package com.example.Used.Repository;

import com.example.Used.Model.Listings;
import com.example.Used.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listings,Long>{
    Optional<Listings> findByIdAndUserId(Long id, Long userId);
    List<Listings> findByStatus(Listings.Status status);

}
