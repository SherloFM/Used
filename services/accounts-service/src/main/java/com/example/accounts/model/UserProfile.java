package com.example.accounts.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;


@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "user_profile")
public class UserProfile {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String firstName;

    @Column
    private String lastName;

    @Column
    private String phoneNumber;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea", name = "img")
    private byte[] img;

    private String imgtype;

    @Column
    private String profileBio;

    /** Relative URL of the avatar image, e.g. "/images/avatar1.jpg" */
    @Column
    private String imgUrl;

    /**
     * Absolute Media URLs written before the relative-path change are reduced
     * to {@code /api/media/{id}} so every existing avatar renders on the site's
     * own origin. Non-Media URLs are returned unchanged.
     */
    public String getImgUrl() {
        return relativeMediaPath(imgUrl);
    }

    private static String relativeMediaPath(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        int marker = url.indexOf("/api/media/");
        if (marker > 0 && (url.startsWith("http://") || url.startsWith("https://"))) {
            return url.substring(marker);
        }
        return url;
    }

    @Column
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @JsonIgnore
    @OneToOne(mappedBy = "userProfile", fetch = FetchType.LAZY)
    private User user;



}