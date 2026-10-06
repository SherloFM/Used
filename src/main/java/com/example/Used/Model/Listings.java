package com.example.Used.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
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
import java.util.HashSet;
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

    @Column
    private String title;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea", name = "img")
    private byte[] img;

    private String imgtype;

    @Column
    private String description;

    @Column
    private double price;

    @Column
    private String condition;

    @Column
    private String location;


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

    @OneToMany(mappedBy = "listing")
    private List<Transactions> transactions;

    @ManyToMany
    @JoinTable(
            name = "listing_categories",
            joinColumns = @JoinColumn(name = "listing_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();
}
