package com.example.Used.Repository;

import com.example.Used.Model.Listings;
import com.example.Used.Model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listings,Long>, JpaSpecificationExecutor<Listings> {
    @Query("SELECT l FROM Listings l LEFT JOIN FETCH l.user WHERE l.id = :id")
    Optional<Listings> findByIdWithUser(@Param("id") Long id);

    Optional<Listings> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Listings l WHERE l.id = :id AND l.user.id = :userId")
    Optional<Listings> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Listings l WHERE l.id = :id")
    Optional<Listings> findByIdForUpdate(@Param("id") Long id);

    /** All listings owned by a user, newest first — the profile's "My Listings" tab. */
    List<Listings> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT l FROM Listings l LEFT JOIN FETCH l.user WHERE l.id = :id AND l.user.id = :userId")
    Optional<Listings> findByIdAndUserIdWithUser(@Param("id") Long id, @Param("userId") Long userId);

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

    /** Layer-1 defense: atomic listing claim. Returns 0 if it was already SOLD/DELISTED. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Listings l SET l.status = :newStatus " +
           "WHERE l.id = :id AND l.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("expectedStatus") Listings.Status expectedStatus,
                              @Param("newStatus") Listings.Status newStatus);
}
