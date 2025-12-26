package com.vintedFav.vintedFavorites.service;

import com.vintedFav.vintedFavorites.model.Favorite;
import com.vintedFav.vintedFavorites.repository.FavoriteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FavoriteService {

    @Autowired
    private FavoriteRepository favoriteRepository;

    public List<Favorite> getAllFavorites() {
        return favoriteRepository.findAllByOrderByFavoriteOrderAsc();
    }

    public List<Favorite> getAllFavorites(String sortOrder) {
        if ("desc".equalsIgnoreCase(sortOrder)) {
            return favoriteRepository.findAllByOrderByFavoriteOrderDesc();
        }
        return favoriteRepository.findAllByOrderByFavoriteOrderAsc();
    }

    public Optional<Favorite> getFavoriteById(Long id) {
        return favoriteRepository.findById(id);
    }

    public Favorite saveFavorite(Favorite favorite) {
        return favoriteRepository.save(favorite);
    }

    public List<Favorite> saveAllFavorites(List<Favorite> favorites) {
        return favoriteRepository.saveAll(favorites);
    }

    public void deleteFavorite(Long id) {
        favoriteRepository.deleteById(id);
    }

    public List<Favorite> getFavoritesByBrand(String brand) {
        return favoriteRepository.findByBrand(brand);
    }

    public List<Favorite> getFavoritesByGender(String gender) {
        return favoriteRepository.findByGender(gender);
    }

    public List<Favorite> getFavoritesByCategory(String category) {
        return favoriteRepository.findByCategory(category);
    }

    public List<Favorite> getFavoritesBySoldStatus(Boolean sold) {
        return favoriteRepository.findBySold(sold);
    }

    public List<Favorite> getFavoritesByVintedId(String vintedId) {
        return favoriteRepository.findByVintedId(vintedId)
                .map(List::of)
                .orElse(List.of());
    }

    public List<Favorite> filterFavorites(String brand, String gender, String category, Boolean sold) {
        // Utiliser la méthode qui trie par ordre d'ajout Vinted
        List<Favorite> favorites = favoriteRepository.findAllByOrderByFavoriteOrderAsc();

        // Filtrage manuel (à optimiser avec des requêtes SQL plus tard)
        return favorites.stream()
                .filter(f -> brand == null || brand.isEmpty() || brand.equalsIgnoreCase(f.getBrand()))
                .filter(f -> gender == null || gender.isEmpty() || gender.equalsIgnoreCase(f.getGender()))
                .filter(f -> category == null || category.isEmpty() || category.equalsIgnoreCase(f.getCategory()))
                .filter(f -> sold == null || sold.equals(f.getSold()))
                .toList();
    }
}