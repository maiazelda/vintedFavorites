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
@Slf4j
public class VintedApiService {

    private final WebClient webClient;
    private final VintedCookieService cookieService;
    private final FavoriteService favoriteService;
    private final ObjectMapper objectMapper;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private VintedAuthService authService;

    public VintedApiService(WebClient webClient, VintedCookieService cookieService,
                           FavoriteService favoriteService, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.cookieService = cookieService;
        this.favoriteService = favoriteService;
        this.objectMapper = objectMapper;
    }

    @Value("${vinted.api.base-url:https://www.vinted.fr}")
    private String baseUrl;

    @Value("${vinted.api.user-id:}")
    private String userId;

    @Value("${vinted.api.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36}")
    private String userAgent;

    public Mono<List<Favorite>> fetchFavorites(int page, int perPage) {
        // Vérifier d'abord si le token est expiré et le rafraîchir si nécessaire
        return authService.ensureValidToken()
                .flatMap(valid -> fetchFavoritesInternal(page, perPage, false));
    }

    private Mono<List<Favorite>> fetchFavoritesInternal(int page, int perPage, boolean isRetry) {
        // Récupérer les cookies frais après un éventuel refresh
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
                .header(HttpHeaders.REFERER, "https://www.vinted.fr/")
                .header(HttpHeaders.ORIGIN, "https://www.vinted.fr")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Ch-Ua", "\"Google Chrome\";v=\"120\", \"Chromium\";v=\"120\", \"Not_A Brand\";v=\"24\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .exchangeToMono(response -> handleResponse(response))
                .map(this::parseFavoritesResponse)
                .onErrorResume(e -> {
                    if (!isRetry && e.getMessage() != null && e.getMessage().contains("401")) {
                        log.warn("Erreur 401 - Tentative de refresh token...");
                        return authService.refreshAccessToken()
                                .flatMap(success -> {
                                    if (success) {
                                        log.info("Token rafraîchi, nouvelle tentative...");
                                        return fetchFavoritesInternal(page, perPage, true);
                                    }
                                    return Mono.error(e);
                                });
                    }
                    return Mono.error(e);
                });
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
            int statusCode = response.statusCode().value();
            log.error("Session expirée ou non autorisée ({}). Veuillez mettre à jour les cookies.", statusCode);
            return Mono.error(new RuntimeException("Erreur " + statusCode + " - Session expirée"));
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

            // Log pour debug - afficher la structure de la réponse
            log.debug("Structure de la réponse: {}", root.toString().substring(0, Math.min(500, root.toString().length())));

            // L'API favorites peut retourner les items dans différents chemins
            JsonNode items = root.path("items");
            if (items.isMissingNode() || !items.isArray()) {
                items = root.path("favourite_items");
            }
            if (items.isMissingNode() || !items.isArray()) {
                items = root.path("item_favourites");
            }

            if (items.isArray()) {
                for (JsonNode itemWrapper : items) {
                    // Les favoris peuvent être wrappés dans un objet "item"
                    JsonNode item = itemWrapper.path("item");
                    if (item.isMissingNode()) {
                        item = itemWrapper;
                    }

                    Favorite favorite = mapJsonToFavorite(item);
                    if (favorite != null) {
                        favorites.add(favorite);
                    }
                }
            }
            log.info("Nombre de favoris récupérés: {}", favorites.size());
        } catch (Exception e) {
            log.error("Erreur lors du parsing de la réponse: {}", e.getMessage());
            log.debug("Réponse brute: {}", responseBody);
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

        // Log pour debug la structure d'un item
        log.debug("Item JSON keys: {}", item.fieldNames().hasNext() ?
                java.util.stream.StreamSupport.stream(
                        java.util.Spliterators.spliteratorUnknownSize(item.fieldNames(), 0), false)
                        .collect(java.util.stream.Collectors.joining(", ")) : "empty");

        Favorite favorite = new Favorite();

        // ID - essayer plusieurs chemins
        favorite.setVintedId(getFirstNonNull(item, "id", "item_id"));

        // Titre
        favorite.setTitle(getFirstNonNull(item, "title", "name"));

        // Brand - essayer plusieurs structures
        String brand = getTextValue(item.path("brand_dto"), "title");
        if (brand == null) brand = getTextValue(item.path("brand"), "title");
        if (brand == null) brand = getFirstNonNull(item, "brand", "brand_title");
        favorite.setBrand(brand);

        // Prix - essayer plusieurs chemins et formats
        Double price = getDoubleValue(item, "price");
        if (price == null) price = getDoubleValue(item.path("price"), "amount");
        if (price == null) price = getDoubleValue(item, "total_item_price");
        favorite.setPrice(price);

        // Image URL - essayer plusieurs structures
        String imageUrl = null;
        JsonNode photos = item.path("photos");
        if (!photos.isMissingNode() && photos.isArray() && photos.size() > 0) {
            imageUrl = getTextValue(photos.get(0), "url");
            if (imageUrl == null) imageUrl = getTextValue(photos.get(0), "full_size_url");
            if (imageUrl == null) imageUrl = getTextValue(photos.get(0), "thumbnails");
        }
        if (imageUrl == null) {
            JsonNode photo = item.path("photo");
            if (!photo.isMissingNode()) {
                imageUrl = getTextValue(photo, "url");
                if (imageUrl == null) imageUrl = getTextValue(photo, "full_size_url");
            }
        }
        if (imageUrl == null) imageUrl = getFirstNonNull(item, "image_url", "thumbnail_url", "photo_url");
        favorite.setImageUrl(imageUrl);

        // URL du produit
        favorite.setProductUrl(getFirstNonNull(item, "url", "path", "item_url"));

        // Vendu
        favorite.setSold(item.path("is_closed").asBoolean(false) ||
                        item.path("is_sold").asBoolean(false) ||
                        "sold".equalsIgnoreCase(getFirstNonNull(item, "status")));

        // Vendeur
        String seller = getTextValue(item.path("user"), "login");
        if (seller == null) seller = getTextValue(item.path("user"), "username");
        if (seller == null) seller = getTextValue(item.path("seller"), "login");
        favorite.setSellerName(seller);

        // Taille
        favorite.setSize(getFirstNonNull(item, "size_title", "size", "size_name"));

        // État/Condition
        favorite.setCondition(getFirstNonNull(item, "status", "condition", "item_status"));

        // Catégorie - essayer plusieurs structures
        String category = getTextValue(item.path("catalog_dto"), "title");
        if (category == null) category = getTextValue(item.path("catalog"), "title");
        if (category == null) category = getFirstNonNull(item, "category", "catalog_title", "category_name");
        favorite.setCategory(category);

        // Genre
        String gender = getFirstNonNull(item, "gender", "gender_title");
        if (gender == null) {
            JsonNode catalogDto = item.path("catalog_dto");
            if (!catalogDto.isMissingNode()) {
                gender = getFirstNonNull(catalogDto, "gender", "gender_title");
            }
        }
        favorite.setGender(gender);

        // Date de publication - essayer plusieurs formats
        long createdTimestamp = item.path("created_at_ts").asLong(0);
        if (createdTimestamp == 0) createdTimestamp = item.path("created_at").asLong(0);
        if (createdTimestamp == 0) createdTimestamp = item.path("updated_at_ts").asLong(0);
        if (createdTimestamp > 0) {
            favorite.setListedDate(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(createdTimestamp),
                    ZoneId.systemDefault()));
        }

        log.debug("Favori mappé: title={}, brand={}, price={}, imageUrl={}",
                favorite.getTitle(), favorite.getBrand(), favorite.getPrice(),
                favorite.getImageUrl() != null ? "présent" : "null");

        return favorite;
    }

    private String getFirstNonNull(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = getTextValue(node, field);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
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
