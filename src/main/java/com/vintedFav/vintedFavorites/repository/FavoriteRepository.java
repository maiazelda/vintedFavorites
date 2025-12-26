package com.vintedFav.vintedFavorites.repository;

import com.vintedFav.vintedFavorites.model.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByBrand(String brand);

    List<Favorite> findByGender(String gender);

    List<Favorite> findByCategory(String category);

    List<Favorite> findBySold(Boolean sold);

    List<Favorite> findByBrandAndGender(String brand, String gender);

    Optional<Favorite> findByVintedId(String vintedId);

    // Récupère tous les favoris triés par ordre d'ajout (ordre Vinted)
    List<Favorite> findAllByOrderByFavoriteOrderAsc();

    // Récupère tous les favoris triés par ordre d'ajout inversé
    List<Favorite> findAllByOrderByFavoriteOrderDesc();

}