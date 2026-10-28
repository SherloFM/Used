package com.example.Used.Repository;

import com.example.Used.Model.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Page<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Favorite> findByListingId(Long listingId);

    Optional<Favorite> findByUserIdAndListingId(Long userId, Long listingId);

    boolean existsByUserIdAndListingId(Long userId, Long listingId);

    long countByListingId(Long listingId);

    void deleteByUserIdAndListingId(Long userId, Long listingId);

    void deleteByListingId(Long listingId);
}
