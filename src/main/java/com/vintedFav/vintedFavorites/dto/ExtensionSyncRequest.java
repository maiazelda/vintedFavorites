package com.vintedFav.vintedFavorites.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO pour recevoir les données de l'extension Chrome.
 * L'extension envoie les favoris récupérés depuis Vinted
 * ainsi que les cookies de session.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionSyncRequest {

    /**
     * Liste des favoris récupérés par l'extension
     */
    private List<ExtensionFavoriteDto> favorites;

    /**
     * Cookies de session Vinted (pour permettre au backend
     * de faire des appels API supplémentaires si nécessaire)
     */
    private List<ExtensionCookieDto> cookies;

    /**
     * DTO pour un favori envoyé par l'extension
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtensionFavoriteDto {
        private String vintedId;
        private String title;
        private String brand;
        private String category;
        private Double price;
        private String size;
        private String condition;
        private String imageUrl;
        private String productUrl;
        private String seller;
        private Boolean sold;
    }

    /**
     * DTO pour un cookie envoyé par l'extension
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtensionCookieDto {
        private String name;
        private String value;
        private String domain;
        private String path;
        private Double expirationDate; // Timestamp en secondes (format Chrome)
    }
}
