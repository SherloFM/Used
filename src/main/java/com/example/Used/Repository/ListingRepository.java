package com.example.Used.Repository;

import com.example.Used.Model.Listings;
import com.example.Used.Model.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listings,Long>{
    Optional<Listings> findByIdAndUserId(Long id, Long userId);

    //filter and sort
    List<Listings> findByStatus(Listings.Status status, Sort sort);

    List<Listings> findByStatusAndTitleContainingIgnoreCase(Listings.Status status, String title, Sort sort);

    // Venn Diagram Search (Category AND logic + optional Text search)
    @Query("SELECT l FROM Listings l JOIN l.categories c " +
            "WHERE l.status = :status " +
            "AND (:keyword IS NULL OR LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND c.id IN :categoryIds " +
            "GROUP BY l.id HAVING COUNT(c.id) = :categoryCount")
    List<Listings> searchByCategories(
            @Param("status") Listings.Status status,
            @Param("keyword") String keyword,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("categoryCount") long categoryCount,
            Sort sort
    );
}
