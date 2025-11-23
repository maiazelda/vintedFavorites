package com.vintedFav.vintedFavorites.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "favorites")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vinted_id", unique = true)
    private String vintedId;

    @Column(nullable = false)
    private String title;

    private String brand;

    private String category; // ex: "Robe", "Pantalon", "Chaussures"

    private String gender; // ex: "Femme", "Homme", "Enfant"

    private Double price;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "product_url", length = 1000)
    private String productUrl;

    @Column(name = "listed_date")
    private LocalDateTime listedDate;

    private Boolean sold = false;

    @Column(name = "seller_name")
    private String sellerName;

    private String size;

    private String condition; // ex: "Neuf", "Très bon état", "Bon état"

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}