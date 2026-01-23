package com.vintedFav.vintedFavorites.service;

import com.vintedFav.vintedFavorites.dto.ExtensionSyncRequest;
import com.vintedFav.vintedFavorites.dto.ExtensionSyncRequest.ExtensionCookieDto;
import com.vintedFav.vintedFavorites.dto.ExtensionSyncRequest.ExtensionFavoriteDto;
import com.vintedFav.vintedFavorites.dto.SyncResponse;
import com.vintedFav.vintedFavorites.model.Favorite;
import com.vintedFav.vintedFavorites.repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service pour traiter les données envoyées par l'extension Chrome.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExtensionSyncService {

    private final FavoriteRepository favoriteRepository;
    private final VintedCookieService cookieService;

    /**
     * Synchronise les favoris et cookies envoyés par l'extension.
     *
     * @param request Les données de l'extension
     * @return Réponse avec le nombre d'items synchronisés
     */
    @Transactional
    public SyncResponse syncFromExtension(ExtensionSyncRequest request) {
        log.info("Réception sync depuis extension: {} favoris, {} cookies",
                request.getFavorites() != null ? request.getFavorites().size() : 0,
                request.getCookies() != null ? request.getCookies().size() : 0);

        // 1. Sauvegarder les cookies
        if (request.getCookies() != null && !request.getCookies().isEmpty()) {
            saveCookies(request.getCookies());
        }

        // 2. Sauvegarder les favoris
        int newItems = 0;
        int totalItems = 0;

        if (request.getFavorites() != null && !request.getFavorites().isEmpty()) {
            SyncResult result = saveFavorites(request.getFavorites());
            newItems = result.newItems;
            totalItems = result.totalItems;
        }

        log.info("Sync extension terminée: {} nouveaux, {} total", newItems, totalItems);

        return new SyncResponse(
                true,
                String.format("Synchronisation réussie: %d nouveaux favoris", newItems),
                newItems,
                totalItems
        );
    }

    /**
     * Sauvegarde les cookies envoyés par l'extension.
     */
    private void saveCookies(List<ExtensionCookieDto> cookies) {
        log.info("Sauvegarde de {} cookies depuis l'extension", cookies.size());

        for (ExtensionCookieDto cookieDto : cookies) {
            try {
                LocalDateTime expiresAt = null;

                // Convertir le timestamp Chrome (secondes) en LocalDateTime
                if (cookieDto.getExpirationDate() != null && cookieDto.getExpirationDate() > 0) {
                    expiresAt = LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(cookieDto.getExpirationDate().longValue()),
                            ZoneId.systemDefault()
                    );
                }

                String domain = cookieDto.getDomain();
                if (domain == null || domain.isEmpty()) {
                    domain = "vinted.fr";
                }

                cookieService.saveCookie(
                        cookieDto.getName(),
                        cookieDto.getValue(),
                        domain,
                        expiresAt
                );
            } catch (Exception e) {
                log.warn("Erreur sauvegarde cookie {}: {}", cookieDto.getName(), e.getMessage());
            }
        }

        log.info("Cookies sauvegardés avec succès");
    }

    /**
     * Sauvegarde les favoris envoyés par l'extension.
     * Met à jour les favoris existants ou crée de nouveaux.
     */
    private SyncResult saveFavorites(List<ExtensionFavoriteDto> favorites) {
        AtomicInteger newCount = new AtomicInteger(0);
        AtomicInteger order = new AtomicInteger(0);

        for (ExtensionFavoriteDto dto : favorites) {
            try {
                if (dto.getVintedId() == null || dto.getVintedId().isEmpty()) {
                    log.warn("Favori ignoré: vintedId manquant");
                    continue;
                }

                // Chercher si le favori existe déjà
                Optional<Favorite> existing = favoriteRepository.findByVintedId(dto.getVintedId());

                Favorite favorite;
                if (existing.isPresent()) {
                    // Mise à jour
                    favorite = existing.get();
                    updateFavoriteFromDto(favorite, dto);
                } else {
                    // Nouveau favori
                    favorite = createFavoriteFromDto(dto);
                    newCount.incrementAndGet();
                }

                // Mettre à jour l'ordre (position dans la liste Vinted)
                favorite.setFavoriteOrder(order.getAndIncrement());

                favoriteRepository.save(favorite);

            } catch (Exception e) {
                log.warn("Erreur sauvegarde favori {}: {}", dto.getVintedId(), e.getMessage());
            }
        }

        return new SyncResult(newCount.get(), favorites.size());
    }

    /**
     * Crée un nouveau Favorite à partir du DTO.
     */
    private Favorite createFavoriteFromDto(ExtensionFavoriteDto dto) {
        Favorite favorite = new Favorite();
        favorite.setVintedId(dto.getVintedId());
        updateFavoriteFromDto(favorite, dto);
        return favorite;
    }

    /**
     * Met à jour un Favorite existant avec les données du DTO.
     */
    private void updateFavoriteFromDto(Favorite favorite, ExtensionFavoriteDto dto) {
        if (dto.getTitle() != null) {
            favorite.setTitle(dto.getTitle());
        }
        if (dto.getBrand() != null) {
            favorite.setBrand(dto.getBrand());
        }
        if (dto.getCategory() != null) {
            favorite.setCategory(dto.getCategory());
        }
        if (dto.getPrice() != null) {
            favorite.setPrice(dto.getPrice());
        }
        if (dto.getSize() != null) {
            favorite.setSize(dto.getSize());
        }
        if (dto.getCondition() != null) {
            favorite.setCondition(dto.getCondition());
        }
        if (dto.getImageUrl() != null) {
            favorite.setImageUrl(dto.getImageUrl());
        }
        if (dto.getProductUrl() != null) {
            favorite.setProductUrl(dto.getProductUrl());
        }
        if (dto.getSeller() != null) {
            favorite.setSellerName(dto.getSeller());
        }
        if (dto.getSold() != null) {
            favorite.setSold(dto.getSold());
        }
    }

    /**
     * Classe interne pour retourner les résultats de sync.
     */
    private static class SyncResult {
        final int newItems;
        final int totalItems;

        SyncResult(int newItems, int totalItems) {
            this.newItems = newItems;
            this.totalItems = totalItems;
        }
    }
}
