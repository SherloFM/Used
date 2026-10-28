package com.example.Used.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonGetter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jdk.jfr.Category;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Component
@Table(name = "listings")
public class Listings {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title cannot be empty")
    @Column
    private String title;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea", name = "img")
    private byte[] img;

    private String imgtype;

    @NotBlank(message = "Description cannot be empty")
    @Column
    private String description;

    @Positive(message = "Price must be greater than 0")
    @Column
    private double price;

    @NotBlank(message = "Condition cannot be empty")
    @Column
    private String condition;

    @NotBlank(message = "Location cannot be empty")
    @Column
    private String location;

    /** Relative URL of the product image, e.g. "/images/laptop.jpg" */
    @Column
    private String imgUrl;


    public enum Status{
        ACTIVE,
        SOLD,
        DELISTED
    }
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Listings.Status status;

    @Column
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Exposes a safe subset of the seller (no password etc.) under the
     * "seller" JSON property, since "user" is @JsonIgnore'd above.
     */
    @JsonGetter("seller")
    public Map<String, Object> getSellerSummary() {
        if (user == null) return null;
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", user.getId());
        summary.put("username", user.getUsername());
        summary.put("email", user.getEmail());
        return summary;
    }

    @OneToMany(mappedBy = "listing")
    @JsonIgnore
    private List<Transactions> transactions;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
            name = "listing_categories",
            joinColumns = @JoinColumn(name = "listing_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Categories> categories = new HashSet<>();
}
