package com.example.Used.Repository;

import com.example.Used.Model.Listings;
import com.example.Used.Model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listings,Long>, JpaSpecificationExecutor<Listings> {
    Optional<Listings> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT l FROM Listings l " +
            "WHERE l.status = :status " +
            "AND (:keyword = '' OR LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:hasCategories = false OR l.id IN (" +
            "SELECT l2.id FROM Listings l2 JOIN l2.categories c2 " +
            "WHERE c2.id IN :categoryIds " +
            "GROUP BY l2.id HAVING COUNT(c2.id) = :categoryCount" +
            "))")
    Page<Listings> searchListingsPaginated(
            @Param("status") Listings.Status status,
            @Param("keyword") String keyword,
            @Param("hasCategories") boolean hasCategories,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("categoryCount") long categoryCount,
            Pageable pageable
    );
}
