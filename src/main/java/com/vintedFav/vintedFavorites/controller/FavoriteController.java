package com.vintedFav.vintedFavorites.controller;

import com.vintedFav.vintedFavorites.model.Favorite;
import com.vintedFav.vintedFavorites.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/favorites")
@CrossOrigin(origins = "http://localhost:3000")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<List<Favorite>> getAllFavorites(
            @RequestParam(required = false, defaultValue = "asc") String sortOrder
    ) {
        List<Favorite> favorites = favoriteService.getAllFavorites(sortOrder);
        return ResponseEntity.ok(favorites);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Favorite> getFavoriteById(@PathVariable Long id) {
        Optional<Favorite> favorite = favoriteService.getFavoriteById(id);
        return favorite.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Favorite> createFavorite(@RequestBody Favorite favorite) {
        Favorite savedFavorite = favoriteService.saveFavorite(favorite);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedFavorite);
    }

    @PostMapping("/import")
    public ResponseEntity<List<Favorite>> importFavorites(@RequestBody List<Favorite> favorites) {
        List<Favorite> savedFavorites = favoriteService.saveAllFavorites(favorites);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedFavorites);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Favorite> updateFavorite(@PathVariable Long id, @RequestBody Favorite favorite) {
        Optional<Favorite> existingFavorite = favoriteService.getFavoriteById(id);
        if (existingFavorite.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        favorite.setId(id);
        Favorite updatedFavorite = favoriteService.saveFavorite(favorite);
        return ResponseEntity.ok(updatedFavorite);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFavorite(@PathVariable Long id) {
        favoriteService.deleteFavorite(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/filter")
    public ResponseEntity<List<Favorite>> filterFavorites(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean sold
    ) {
        List<Favorite> filteredFavorites = favoriteService.filterFavorites(brand, gender, category, sold);
        return ResponseEntity.ok(filteredFavorites);
    }

    @GetMapping("/brands")
    public ResponseEntity<List<String>> getAllBrands() {
        List<String> brands = favoriteService.getAllFavorites().stream()
                .map(Favorite::getBrand)
                .filter(brand -> brand != null && !brand.isEmpty())
                .distinct()
                .sorted()
                .toList();
        return ResponseEntity.ok(brands);
    }
}