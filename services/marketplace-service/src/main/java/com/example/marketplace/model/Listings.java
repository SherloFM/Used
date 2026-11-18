package com.example.marketplace.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /**
     * Rows written by an earlier deployment can hold an absolute Media URL such
     * as {@code http://localhost:8088/api/media/{id}}, which no browser other
     * than the one on the server can load. Return only the path for those rows
     * so images that are already stored keep working without a migration.
     * External image URLs (anything not served by Media) are untouched.
     */
    public String getImgUrl() {
        return relativeMediaPath(imgUrl);
    }

    /** Shared with {@code UserProfile} semantics: never rewrite a non-Media URL. */
    public static String relativeMediaPath(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        int marker = url.indexOf("/api/media/");
        if (marker > 0 && (url.startsWith("http://") || url.startsWith("https://"))) {
            return url.substring(marker);
        }
        return url;
    }


    public enum Status{
        ACTIVE,
        SOLD,
        DELISTED
    }
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Listings.Status status;

    /**
     * Why the listing is no longer ACTIVE (null while ACTIVE/SOLD): e.g.
     * "Removed by you", "Removed by administrator",
     * "Removed by moderation (report #12)". Shown on the seller's own listings.
     */
    @Column
    private String statusReason;

    @Column
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Seller id. The FK column is unchanged (user_id); only the Java mapping is
     * scalar, so this service no longer compiles against the Accounts entity.
     */
    @JsonIgnore
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Seller display data for the JSON "seller" property, filled by
     * ListingService from AccountsClient on the detail path. Not persisted:
     * the source of truth stays in Accounts.
     */
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Map<String, Object> seller;

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
