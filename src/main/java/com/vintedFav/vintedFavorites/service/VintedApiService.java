package com.vintedFav.vintedFavorites.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vintedFav.vintedFavorites.model.Favorite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VintedApiService {

    private final WebClient webClient;
    private final VintedCookieService cookieService;
    private final FavoriteService favoriteService;
    private final ObjectMapper objectMapper;

    @Value("${vinted.api.base-url:https://www.vinted.fr}")
    private String baseUrl;

    @Value("${vinted.api.user-id:}")
    private String userId;

    @Value("${vinted.api.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36}")
    private String userAgent;

    public Mono<List<Favorite>> fetchFavorites(int page, int perPage) {
        String cookieHeader = cookieService.buildCookieHeader();

        if (cookieHeader.isEmpty()) {
            log.warn("Aucun cookie actif trouvé. Veuillez d'abord configurer les cookies.");
            return Mono.error(new RuntimeException("Cookies non configurés"));
        }

        if (userId == null || userId.isEmpty()) {
            log.error("User ID non configuré. Veuillez définir vinted.api.user-id dans application.properties");
            return Mono.error(new RuntimeException("User ID non configuré"));
        }

        String url = baseUrl + "/api/v2/users/" + userId + "/items/favourites?page=" + page + "&per_page=" + perPage;
        log.info("Appel API Vinted: {}", url);

        return webClient.get()
                .uri(url)
                .header(HttpHeaders.COOKIE, cookieHeader)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .header(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .header("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header(HttpHeaders.REFERER, "https://www.vinted.fr/")
                .header(HttpHeaders.ORIGIN, "https://www.vinted.fr")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Ch-Ua", "\"Google Chrome\";v=\"120\", \"Chromium\";v=\"120\", \"Not_A Brand\";v=\"24\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .exchangeToMono(this::handleResponse)
                .map(this::parseFavoritesResponse);
    }

    public Mono<Favorite> fetchItemDetails(String itemId) {
        String cookieHeader = cookieService.buildCookieHeader();

        if (cookieHeader.isEmpty()) {
            return Mono.error(new RuntimeException("Cookies non configurés"));
        }

        String url = baseUrl + "/api/v2/items/" + itemId;
        log.info("Récupération des détails de l'article: {}", itemId);

        return webClient.get()
                .uri(url)
                .header(HttpHeaders.COOKIE, cookieHeader)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .exchangeToMono(this::handleResponse)
                .map(this::parseItemResponse);
    }

    private Mono<String> handleResponse(ClientResponse response) {
        // Gérer les cookies de la réponse
        response.headers().header(HttpHeaders.SET_COOKIE).forEach(setCookie -> {
            log.debug("Cookie reçu: {}", setCookie);
            cookieService.updateCookiesFromResponse(setCookie);
        });

        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(String.class);
        } else if (response.statusCode().value() == 401 || response.statusCode().value() == 403) {
            log.error("Session expirée ou non autorisée. Veuillez mettre à jour les cookies.");
            return Mono.error(new RuntimeException("Session expirée - cookies à mettre à jour"));
        } else {
            log.error("Erreur API Vinted: {}", response.statusCode());
            return response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new RuntimeException("Erreur API: " + response.statusCode() + " - " + body)));
        }
    }

    private List<Favorite> parseFavoritesResponse(String responseBody) {
        List<Favorite> favorites = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode items = root.path("items");

            if (items.isArray()) {
                for (JsonNode item : items) {
                    Favorite favorite = mapJsonToFavorite(item);
                    if (favorite != null) {
                        favorites.add(favorite);
                    }
                }
            }
            log.info("Nombre de favoris récupérés: {}", favorites.size());
        } catch (Exception e) {
            log.error("Erreur lors du parsing de la réponse: {}", e.getMessage());
        }

        return favorites;
    }

    private Favorite parseItemResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode item = root.path("item");
            return mapJsonToFavorite(item);
        } catch (Exception e) {
            log.error("Erreur lors du parsing de l'article: {}", e.getMessage());
            return null;
        }
    }

    private Favorite mapJsonToFavorite(JsonNode item) {
        if (item == null || item.isMissingNode()) {
            return null;
        }

        Favorite favorite = new Favorite();

        favorite.setVintedId(getTextValue(item, "id"));
        favorite.setTitle(getTextValue(item, "title"));
        favorite.setBrand(getTextValue(item.path("brand_dto"), "title"));
        favorite.setPrice(getDoubleValue(item, "price"));

        // Image URL
        JsonNode photos = item.path("photos");
        if (photos.isArray() && photos.size() > 0) {
            favorite.setImageUrl(getTextValue(photos.get(0), "url"));
        }

        favorite.setProductUrl(getTextValue(item, "url"));
        favorite.setSold(item.path("is_closed").asBoolean(false));
        favorite.setSellerName(getTextValue(item.path("user"), "login"));
        favorite.setSize(getTextValue(item, "size_title"));
        favorite.setCondition(getTextValue(item, "status"));

        // Catégorie
        JsonNode catalog = item.path("catalog_dto");
        if (!catalog.isMissingNode()) {
            favorite.setCategory(getTextValue(catalog, "title"));
        }

        // Date de publication
        long createdTimestamp = item.path("created_at_ts").asLong(0);
        if (createdTimestamp > 0) {
            favorite.setListedDate(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(createdTimestamp),
                    ZoneId.systemDefault()));
        }

        return favorite;
    }

    private String getTextValue(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asText();
    }

    private Double getDoubleValue(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        // Vinted retourne parfois le prix comme string
        if (fieldNode.isTextual()) {
            try {
                return Double.parseDouble(fieldNode.asText().replace(",", "."));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return fieldNode.asDouble();
    }

    public Mono<Integer> syncAllFavorites() {
        return fetchAllFavoritesPages()
                .map(favorites -> {
                    int savedCount = 0;
                    for (Favorite favorite : favorites) {
                        try {
                            // Vérifier si l'article existe déjà
                            var existing = favoriteService.getFavoritesByVintedId(favorite.getVintedId());
                            if (existing.isEmpty()) {
                                favoriteService.saveFavorite(favorite);
                                savedCount++;
                                log.info("Nouveau favori sauvegardé: {}", favorite.getTitle());
                            } else {
                                // Mettre à jour les informations existantes
                                Favorite existingFavorite = existing.get(0);
                                updateExistingFavorite(existingFavorite, favorite);
                                favoriteService.saveFavorite(existingFavorite);
                                log.debug("Favori mis à jour: {}", favorite.getTitle());
                            }
                        } catch (Exception e) {
                            log.error("Erreur lors de la sauvegarde du favori: {}", e.getMessage());
                        }
                    }
                    return savedCount;
                });
    }

    private void updateExistingFavorite(Favorite existing, Favorite updated) {
        existing.setPrice(updated.getPrice());
        existing.setSold(updated.getSold());
        existing.setTitle(updated.getTitle());
        existing.setImageUrl(updated.getImageUrl());
        existing.setCondition(updated.getCondition());
    }

    private Mono<List<Favorite>> fetchAllFavoritesPages() {
        return fetchFavoritesRecursively(1, 20, new ArrayList<>());
    }

    private Mono<List<Favorite>> fetchFavoritesRecursively(int page, int perPage, List<Favorite> accumulated) {
        return fetchFavorites(page, perPage)
                .flatMap(favorites -> {
                    accumulated.addAll(favorites);
                    if (favorites.size() < perPage) {
                        // Dernière page
                        return Mono.just(accumulated);
                    } else {
                        // Récupérer la page suivante
                        return fetchFavoritesRecursively(page + 1, perPage, accumulated);
                    }
                });
    }

    public boolean isSessionValid() {
        return cookieService.hasValidSession();
    }
}
