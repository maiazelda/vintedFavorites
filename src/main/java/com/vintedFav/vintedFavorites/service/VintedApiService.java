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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private VintedSessionService sessionService;

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

        // Récupérer les headers supplémentaires
        String csrfToken = cookieService.getCsrfToken();
        String anonId = cookieService.getAnonId();

        var requestSpec = webClient.get()
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
                .header("Sec-Ch-Ua", "\"Google Chrome\";v=\"142\", \"Chromium\";v=\"142\", \"Not_A Brand\";v=\"99\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"");

        // Ajouter X-Csrf-Token si disponible
        if (csrfToken != null && !csrfToken.isEmpty()) {
            requestSpec = requestSpec.header("X-Csrf-Token", csrfToken);
        }
        // Ajouter X-Anon-Id si disponible
        if (anonId != null && !anonId.isEmpty()) {
            requestSpec = requestSpec.header("X-Anon-Id", anonId);
        }

        return requestSpec
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
        return authService.ensureValidToken()
                .flatMap(valid -> fetchItemDetailsInternal(itemId, false));
    }

    private Mono<Favorite> fetchItemDetailsInternal(String itemId, boolean isRetry) {
        String cookieHeader = cookieService.buildCookieHeader();

        if (cookieHeader.isEmpty()) {
            return Mono.error(new RuntimeException("Cookies non configurés"));
        }

        String url = baseUrl + "/api/v2/items/" + itemId;
        log.debug("Récupération des détails de l'article: {}", itemId);

        // Récupérer les headers supplémentaires
        String csrfToken = cookieService.getCsrfToken();
        String anonId = cookieService.getAnonId();

        var requestSpec = webClient.get()
                .uri(url)
                .header(HttpHeaders.COOKIE, cookieHeader)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .header(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .header("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
                .header(HttpHeaders.REFERER, "https://www.vinted.fr/items/" + itemId)
                .header(HttpHeaders.ORIGIN, "https://www.vinted.fr")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Ch-Ua", "\"Google Chrome\";v=\"142\", \"Chromium\";v=\"142\", \"Not_A Brand\";v=\"99\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"");

        // Ajouter X-Csrf-Token si disponible
        if (csrfToken != null && !csrfToken.isEmpty()) {
            requestSpec = requestSpec.header("X-Csrf-Token", csrfToken);
        }
        // Ajouter X-Anon-Id si disponible
        if (anonId != null && !anonId.isEmpty()) {
            requestSpec = requestSpec.header("X-Anon-Id", anonId);
        }

        return requestSpec
                .exchangeToMono(response -> handleItemDetailsResponse(response, itemId))
                .flatMap(body -> {
                    if (body == null) {
                        return Mono.empty(); // Article non trouvé (404)
                    }
                    Favorite favorite = parseItemResponse(body);
                    return favorite != null ? Mono.just(favorite) : Mono.empty();
                })
                .onErrorResume(e -> {
                    if (!isRetry && e.getMessage() != null && e.getMessage().contains("401")) {
                        log.warn("Erreur 401 sur item {} - Tentative de refresh token...", itemId);
                        return authService.refreshAccessToken()
                                .flatMap(success -> {
                                    if (success) {
                                        return fetchItemDetailsInternal(itemId, true);
                                    }
                                    return Mono.empty();
                                });
                    }
                    log.debug("Erreur sur item {}: {}", itemId, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Handler spécifique pour les détails d'articles - gère les 404 gracieusement
     */
    private Mono<String> handleItemDetailsResponse(ClientResponse response, String itemId) {
        // Gérer les cookies de la réponse
        response.headers().header(HttpHeaders.SET_COOKIE).forEach(setCookie -> {
            log.debug("Cookie reçu: {}", setCookie);
            cookieService.updateCookiesFromResponse(setCookie);
        });

        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(String.class);
        } else if (response.statusCode().value() == 404) {
            // Article supprimé ou non disponible - c'est normal, on ignore
            log.debug("Article {} non trouvé (404) - probablement supprimé ou vendu", itemId);
            return Mono.just(""); // Retourner une chaîne vide pour indiquer "non trouvé"
        } else if (response.statusCode().value() == 401 || response.statusCode().value() == 403) {
            int statusCode = response.statusCode().value();
            return Mono.error(new RuntimeException("Erreur " + statusCode + " - Session expirée"));
        } else {
            log.warn("Erreur API pour article {}: {}", itemId, response.statusCode());
            return Mono.just(""); // Ignorer les autres erreurs
        }
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

            // Déclencher un rafraîchissement automatique de session si les credentials sont configurés
            if (sessionService != null && sessionService.hasCredentials() && !sessionService.isRefreshInProgress()) {
                log.info("Déclenchement du rafraîchissement automatique de session...");
                sessionService.refreshSession()
                        .thenAccept(success -> {
                            if (success) {
                                log.info("Session rafraîchie automatiquement - réessayez la requête");
                            } else {
                                log.error("Échec du rafraîchissement automatique de session");
                            }
                        });
            }

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
        // Réponse vide = article non trouvé
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode item = root.path("item");

            // Vérifier que la réponse contient bien un item
            if (item.isMissingNode()) {
                log.debug("Réponse sans 'item' - structure inattendue");
                return null;
            }

            Favorite favorite = mapJsonToFavorite(item);

            // Enrichir avec les champs supplémentaires disponibles dans le détail
            if (favorite != null) {
                enrichFavoriteWithDetails(favorite, item);
            }

            return favorite;
        } catch (Exception e) {
            log.debug("Erreur lors du parsing de l'article: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Enrichit un favori avec les champs détaillés (category, gender, listedDate)
     * disponibles uniquement dans l'endpoint /api/v2/items/{id}
     */
    private void enrichFavoriteWithDetails(Favorite favorite, JsonNode item) {
        // Catégorie - depuis catalog_id ou catalog
        String category = null;
        JsonNode catalogNode = item.path("catalog");
        if (!catalogNode.isMissingNode()) {
            category = getTextValue(catalogNode, "title");
        }
        if (category == null) {
            category = getTextValue(item, "catalog_title");
        }
        // Essayer aussi catalog_tree pour avoir la catégorie parent
        if (category == null) {
            JsonNode catalogTree = item.path("catalog_tree");
            if (catalogTree.isArray() && catalogTree.size() > 0) {
                // Prendre la dernière catégorie (la plus spécifique)
                category = getTextValue(catalogTree.get(catalogTree.size() - 1), "title");
            }
        }
        favorite.setCategory(category);

        // Genre - depuis gender ou catalog
        String gender = getTextValue(item, "gender");
        if (gender == null) {
            // Essayer d'extraire depuis la catégorie parente
            JsonNode catalogTree = item.path("catalog_tree");
            if (catalogTree.isArray() && catalogTree.size() > 0) {
                String topCategory = getTextValue(catalogTree.get(0), "title");
                if (topCategory != null) {
                    if (topCategory.toLowerCase().contains("femme") || topCategory.toLowerCase().contains("women")) {
                        gender = "Femme";
                    } else if (topCategory.toLowerCase().contains("homme") || topCategory.toLowerCase().contains("men")) {
                        gender = "Homme";
                    } else if (topCategory.toLowerCase().contains("enfant") || topCategory.toLowerCase().contains("kids")) {
                        gender = "Enfant";
                    }
                }
            }
        }
        favorite.setGender(gender);

        // Date de publication - created_at_ts est un timestamp Unix
        long createdTimestamp = item.path("created_at_ts").asLong(0);
        if (createdTimestamp == 0) {
            createdTimestamp = item.path("created_at").asLong(0);
        }
        if (createdTimestamp > 0) {
            favorite.setListedDate(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(createdTimestamp),
                    ZoneId.systemDefault()));
        }

        log.debug("Détails enrichis pour {}: category={}, gender={}, listedDate={}",
                favorite.getTitle(), favorite.getCategory(), favorite.getGender(), favorite.getListedDate());
    }

    private Favorite mapJsonToFavorite(JsonNode item) {
        if (item == null || item.isMissingNode()) {
            return null;
        }

        Favorite favorite = new Favorite();

        // ID (peut être un nombre)
        JsonNode idNode = item.path("id");
        if (!idNode.isMissingNode()) {
            favorite.setVintedId(idNode.isNumber() ? String.valueOf(idNode.asLong()) : idNode.asText());
        }

        // Titre
        favorite.setTitle(getTextValue(item, "title"));

        // Brand - le champ est "brand_title" directement
        favorite.setBrand(getTextValue(item, "brand_title"));

        // Prix - nested object avec "amount"
        JsonNode priceNode = item.path("price");
        if (!priceNode.isMissingNode()) {
            String priceStr = getTextValue(priceNode, "amount");
            if (priceStr != null) {
                try {
                    favorite.setPrice(Double.parseDouble(priceStr.replace(",", ".")));
                } catch (NumberFormatException e) {
                    log.warn("Impossible de parser le prix: {}", priceStr);
                }
            }
        }

        // Image URL - le champ est "photo" (pas "photos")
        JsonNode photo = item.path("photo");
        if (!photo.isMissingNode()) {
            String imageUrl = getTextValue(photo, "url");
            if (imageUrl == null) {
                imageUrl = getTextValue(photo, "full_size_url");
            }
            favorite.setImageUrl(imageUrl);
        }

        // URL du produit
        favorite.setProductUrl(getTextValue(item, "url"));

        // Vendu (is_closed)
        favorite.setSold(item.path("is_closed").asBoolean(false));

        // Vendeur
        JsonNode user = item.path("user");
        if (!user.isMissingNode()) {
            favorite.setSellerName(getTextValue(user, "login"));
        }

        // Taille
        String size = getTextValue(item, "size_title");
        if (size == null) size = getTextValue(item, "size");
        favorite.setSize(size);

        // État/Condition (le champ "status" contient l'état)
        favorite.setCondition(getTextValue(item, "status"));

        // Enrichir avec category, gender et listedDate si disponibles dans le JSON
        enrichFavoriteWithDetails(favorite, item);

        log.debug("Favori mappé: id={}, title={}, brand={}, price={}, category={}, gender={}, imageUrl={}",
                favorite.getVintedId(), favorite.getTitle(), favorite.getBrand(),
                favorite.getPrice(), favorite.getCategory(), favorite.getGender(),
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
                .flatMap(favorites -> {
                    int savedCount = 0;
                    List<Favorite> favoritesToEnrich = new ArrayList<>();

                    for (Favorite favorite : favorites) {
                        try {
                            // Vérifier si l'article existe déjà
                            var existing = favoriteService.getFavoritesByVintedId(favorite.getVintedId());
                            if (existing.isEmpty()) {
                                favoriteService.saveFavorite(favorite);
                                savedCount++;
                                favoritesToEnrich.add(favorite);
                                log.info("Nouveau favori sauvegardé: {}", favorite.getTitle());
                            } else {
                                // Mettre à jour les informations existantes
                                Favorite existingFavorite = existing.get(0);
                                updateExistingFavorite(existingFavorite, favorite);
                                favoriteService.saveFavorite(existingFavorite);

                                // Enrichir si les détails manquent
                                if (needsEnrichment(existingFavorite)) {
                                    favoritesToEnrich.add(existingFavorite);
                                }
                                log.debug("Favori mis à jour: {}", favorite.getTitle());
                            }
                        } catch (Exception e) {
                            log.error("Erreur lors de la sauvegarde du favori: {}", e.getMessage());
                        }
                    }

                    final int finalSavedCount = savedCount;

                    // Enrichir les favoris qui en ont besoin
                    if (!favoritesToEnrich.isEmpty()) {
                        log.info("Enrichissement de {} favoris avec les détails...", favoritesToEnrich.size());
                        return enrichFavorites(favoritesToEnrich)
                                .thenReturn(finalSavedCount);
                    }

                    return Mono.just(finalSavedCount);
                });
    }

    /**
     * Vérifie si un favori a besoin d'être enrichi avec les détails
     */
    private boolean needsEnrichment(Favorite favorite) {
        return favorite.getCategory() == null ||
               favorite.getGender() == null ||
               favorite.getListedDate() == null;
    }

    /**
     * Enrichit une liste de favoris avec les détails (category, gender, listedDate)
     * en appelant l'API de détail pour chaque article
     */
    public Mono<Void> enrichFavorites(List<Favorite> favorites) {
        if (favorites.isEmpty()) {
            return Mono.empty();
        }

        AtomicInteger enrichedCount = new AtomicInteger(0);
        AtomicInteger index = new AtomicInteger(0);

        return Mono.defer(() -> enrichNextFavorite(favorites, index, enrichedCount))
                .doOnTerminate(() -> log.info("Enrichissement terminé: {}/{} favoris enrichis",
                        enrichedCount.get(), favorites.size()));
    }

    private Mono<Void> enrichNextFavorite(List<Favorite> favorites, AtomicInteger index, AtomicInteger enrichedCount) {
        int currentIndex = index.getAndIncrement();
        if (currentIndex >= favorites.size()) {
            return Mono.empty();
        }

        Favorite favorite = favorites.get(currentIndex);
        log.debug("Enrichissement {}/{}: {}", currentIndex + 1, favorites.size(), favorite.getTitle());

        return fetchItemDetails(favorite.getVintedId())
                .delaySubscription(Duration.ofMillis(300)) // Délai avant l'appel pour éviter le rate limiting
                .doOnNext(details -> {
                    // Mettre à jour le favori avec les détails
                    favorite.setCategory(details.getCategory());
                    favorite.setGender(details.getGender());
                    favorite.setListedDate(details.getListedDate());
                    favoriteService.saveFavorite(favorite);
                    enrichedCount.incrementAndGet();
                    log.info("Favori enrichi: {} - category={}, gender={}, listedDate={}",
                            favorite.getTitle(), details.getCategory(), details.getGender(), details.getListedDate());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Article {} non disponible pour enrichissement", favorite.getTitle());
                    return Mono.empty();
                }))
                .onErrorResume(e -> {
                    log.debug("Erreur enrichissement {}: {}", favorite.getTitle(), e.getMessage());
                    return Mono.empty();
                })
                .then(Mono.defer(() -> enrichNextFavorite(favorites, index, enrichedCount)));
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
