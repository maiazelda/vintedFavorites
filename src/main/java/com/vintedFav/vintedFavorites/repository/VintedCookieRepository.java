package com.vintedFav.vintedFavorites.repository;

import com.vintedFav.vintedFavorites.model.VintedCookie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VintedCookieRepository extends JpaRepository<VintedCookie, Long> {

    Optional<VintedCookie> findByCookieName(String cookieName);

    List<VintedCookie> findByIsActiveTrue();

    List<VintedCookie> findByDomain(String domain);

    @Modifying
    @Query("UPDATE VintedCookie c SET c.isActive = false WHERE c.cookieName = :cookieName")
    void deactivateByCookieName(String cookieName);

    @Modifying
    @Query("UPDATE VintedCookie c SET c.isActive = false")
    void deactivateAll();

    void deleteByCookieName(String cookieName);
}
