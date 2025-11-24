package com.vintedFav.vintedFavorites.repository;

import com.vintedFav.vintedFavorites.model.VintedCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VintedCredentialsRepository extends JpaRepository<VintedCredentials, Long> {

    Optional<VintedCredentials> findByIsActiveTrue();

    Optional<VintedCredentials> findByEmail(String email);

    @Modifying
    @Query("UPDATE VintedCredentials c SET c.isActive = false")
    void deactivateAll();
}
