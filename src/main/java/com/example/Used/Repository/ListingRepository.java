package com.example.Used.Repository;

import com.example.Used.Model.Listings;
import com.example.Used.Model.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listings,Long>, JpaSpecificationExecutor<Listings> {
    Optional<Listings> findByIdAndUserId(Long id, Long userId);

    //filter and sort
    List<Listings> findByStatus(Listings.Status status, Sort sort);

    List<Listings> findByStatusAndTitleContainingIgnoreCase(Listings.Status status, String title, Sort sort);
}
