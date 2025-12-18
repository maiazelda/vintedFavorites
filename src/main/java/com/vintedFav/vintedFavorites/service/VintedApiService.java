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

    @Value("${vinted.api.enrichment-delay:2000}")
    private int enrichmentDelayMs;

    @Value("${vinted.api.max-enrichment-batch:20}")
    private int maxEnrichmentBatch;

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

            // Log DEBUG pour voir les champs disponibles dans l'API de détail
            log.info("📦 API détail - Champs disponibles: {}", iteratorToString(item.fieldNames()));

            // Log des champs importants pour category/gender
            log.info("📦 API détail - catalog: {}", item.has("catalog") ? item.path("catalog") : "ABSENT");
            log.info("📦 API détail - catalog_tree: {}", item.has("catalog_tree") ? item.path("catalog_tree") : "ABSENT");
            log.info("📦 API détail - gender: {}", item.has("gender") ? item.path("gender") : "ABSENT");

            Favorite favorite = mapJsonToFavorite(item);

            // Enrichir avec les champs supplémentaires disponibles dans le détail
            if (favorite != null) {
                enrichFavoriteWithDetails(favorite, item);
            }

            return favorite;
        } catch (Exception e) {
            log.error("Erreur lors du parsing de l'article: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Enrichit un favori avec les champs détaillés (category, gender, listedDate)
     * disponibles uniquement dans l'endpoint /api/v2/items/{id}
     */
    private void enrichFavoriteWithDetails(Favorite favorite, JsonNode item) {
        // Log de debug pour voir tous les champs disponibles dans le JSON
        log.debug("🔍 JSON fields disponibles pour item {}: {}", favorite.getVintedId(),
                item.fieldNames() != null ? iteratorToString(item.fieldNames()) : "null");

        // Catégorie - depuis catalog_id ou catalog
        String category = null;

        // 1. Essayer catalog.title
        JsonNode catalogNode = item.path("catalog");
        if (!catalogNode.isMissingNode() && !catalogNode.isNull()) {
            category = getTextValue(catalogNode, "title");
            if (category != null) log.debug("Category from catalog.title: {}", category);
        }

        // 2. Essayer catalog_title directement
        if (category == null) {
            category = getTextValue(item, "catalog_title");
            if (category != null) log.debug("Category from catalog_title: {}", category);
        }

        // 3. Essayer catalog_tree pour avoir la catégorie la plus spécifique
        if (category == null) {
            JsonNode catalogTree = item.path("catalog_tree");
            if (catalogTree.isArray() && catalogTree.size() > 0) {
                // Prendre la dernière catégorie (la plus spécifique)
                category = getTextValue(catalogTree.get(catalogTree.size() - 1), "title");
                if (category != null) log.debug("Category from catalog_tree[last]: {}", category);
            }
        }

        // 4. Essayer category directement
        if (category == null) {
            category = getTextValue(item, "category");
            if (category != null) log.debug("Category from category field: {}", category);
        }

        // 5. Essayer service_fee_catalog_title
        if (category == null) {
            category = getTextValue(item, "service_fee_catalog_title");
            if (category != null) log.debug("Category from service_fee_catalog_title: {}", category);
        }

        // 6. Essayer catalog_branch_title
        if (category == null) {
            category = getTextValue(item, "catalog_branch_title");
            if (category != null) log.debug("Category from catalog_branch_title: {}", category);
        }

        if (category == null) {
            log.warn("⚠️  Category not found for item: {} (vintedId: {}). JSON keys: {}",
                    favorite.getTitle(), favorite.getVintedId(),
                    iteratorToString(item.fieldNames()));
        }
        favorite.setCategory(category);

        // Genre - depuis gender ou catalog
        String gender = null;

        // 1. Essayer gender directement
        gender = getTextValue(item, "gender");
        if (gender != null) {
            log.debug("Gender from gender field: {}", gender);
        }

        // 2. Essayer user.gender (genre du vendeur, pas toujours pertinent mais utile)
        if (gender == null) {
            JsonNode userNode = item.path("user");
            if (!userNode.isMissingNode()) {
                gender = getTextValue(userNode, "gender");
                if (gender != null) log.debug("Gender from user.gender: {}", gender);
            }
        }

        // 3. Inférer depuis catalog_tree
        if (gender == null) {
            JsonNode catalogTree = item.path("catalog_tree");
            if (catalogTree.isArray() && catalogTree.size() > 0) {
                // Parcourir tous les niveaux pour trouver un indicateur de genre
                for (JsonNode categoryNode : catalogTree) {
                    String catTitle = getTextValue(categoryNode, "title");
                    if (catTitle != null) {
                        String catLower = catTitle.toLowerCase();
                        if (catLower.contains("femme") || catLower.contains("women") || catLower.equals("femmes")) {
                            gender = "Femme";
                            log.debug("Gender inferred from catalog_tree '{}': Femme", catTitle);
                            break;
                        } else if (catLower.contains("homme") || catLower.contains("men") || catLower.equals("hommes")) {
                            gender = "Homme";
                            log.debug("Gender inferred from catalog_tree '{}': Homme", catTitle);
                            break;
                        } else if (catLower.contains("enfant") || catLower.contains("kids") || catLower.contains("bébé") ||
                                   catLower.contains("fille") || catLower.contains("garçon")) {
                            gender = "Enfant";
                            log.debug("Gender inferred from catalog_tree '{}': Enfant", catTitle);
                            break;
                        }
                    }
                }
            }
        }

        // 4. Inférer depuis catalog.title si disponible
        if (gender == null && catalogNode != null && !catalogNode.isMissingNode()) {
            String catalogTitle = getTextValue(catalogNode, "title");
            if (catalogTitle != null) {
                String catLower = catalogTitle.toLowerCase();
                if (catLower.contains("femme") || catLower.contains("women")) {
                    gender = "Femme";
                    log.debug("Gender inferred from catalog.title '{}': Femme", catalogTitle);
                } else if (catLower.contains("homme") || catLower.contains("men")) {
                    gender = "Homme";
                    log.debug("Gender inferred from catalog.title '{}': Homme", catalogTitle);
                } else if (catLower.contains("enfant") || catLower.contains("kids")) {
                    gender = "Enfant";
                    log.debug("Gender inferred from catalog.title '{}': Enfant", catalogTitle);
                }
            }
        }

        // 5. Inférer depuis l'URL du produit
        if (gender == null) {
            String productUrl = getTextValue(item, "url");
            if (productUrl != null) {
                String urlLower = productUrl.toLowerCase();
                if (urlLower.contains("/femmes/") || urlLower.contains("/women/")) {
                    gender = "Femme";
                    log.debug("Gender inferred from URL: Femme");
                } else if (urlLower.contains("/hommes/") || urlLower.contains("/men/")) {
                    gender = "Homme";
                    log.debug("Gender inferred from URL: Homme");
                } else if (urlLower.contains("/enfants/") || urlLower.contains("/kids/")) {
                    gender = "Enfant";
                    log.debug("Gender inferred from URL: Enfant");
                }
            }
        }

        if (gender == null) {
            log.warn("⚠️  Gender not found for item: {} (vintedId: {})", favorite.getTitle(), favorite.getVintedId());
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

        log.info("✓ Détails enrichis pour '{}': category={}, gender={}, listedDate={}",
                favorite.getTitle(), favorite.getCategory(), favorite.getGender(), favorite.getListedDate());
    }

    /**
     * Convertit un Iterator<String> en String pour le logging
     */
    private String iteratorToString(java.util.Iterator<String> iterator) {
        if (iterator == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        while (iterator.hasNext()) {
            sb.append(iterator.next());
            if (iterator.hasNext()) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
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

        // NOTE: category, gender et listedDate ne sont PAS disponibles dans l'endpoint /favourites
        // Ces champs seront enrichis via fetchItemDetails() qui appelle /api/v2/items/{id}

        log.debug("Favori mappé: id={}, title={}, brand={}, price={}, imageUrl={}",
                favorite.getVintedId(), favorite.getTitle(), favorite.getBrand(),
                favorite.getPrice(), favorite.getImageUrl() != null ? "présent" : "null");

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

                                // Toujours enrichir les nouveaux favoris si category ou gender manquent
                                if (needsEnrichment(favorite)) {
                                    favoritesToEnrich.add(favorite);
                                    log.info("Nouveau favori sauvegardé (besoin d'enrichissement): {}", favorite.getTitle());
                                } else {
                                    log.info("Nouveau favori sauvegardé (complet): {}", favorite.getTitle());
                                }
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
     * IMPORTANT: Limite le nombre de favoris enrichis pour éviter le rate limiting (429)
     */
    public Mono<Void> enrichFavorites(List<Favorite> favorites) {
        if (favorites.isEmpty()) {
            return Mono.empty();
        }

        // Limiter le nombre de favoris à enrichir pour éviter le rate limiting
        List<Favorite> limitedFavorites = favorites.size() > maxEnrichmentBatch
                ? favorites.subList(0, maxEnrichmentBatch)
                : favorites;

        if (favorites.size() > maxEnrichmentBatch) {
            log.warn("⚠️  Limitation: enrichissement de {} favoris sur {} pour éviter le rate limiting (429). " +
                    "Relancez l'enrichissement pour continuer.", maxEnrichmentBatch, favorites.size());
        }

        AtomicInteger enrichedCount = new AtomicInteger(0);
        AtomicInteger index = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        return Mono.defer(() -> enrichNextFavorite(limitedFavorites, index, enrichedCount, errorCount))
                .doOnTerminate(() -> {
                    log.info("Enrichissement terminé: {}/{} favoris enrichis, {} erreurs",
                            enrichedCount.get(), limitedFavorites.size(), errorCount.get());
                    if (favorites.size() > maxEnrichmentBatch) {
                        log.info("💡 Astuce: {} favoris restants. Relancez POST /api/vinted/favorites/enrich",
                                favorites.size() - maxEnrichmentBatch);
                    }
                });
    }

    private Mono<Void> enrichNextFavorite(List<Favorite> favorites, AtomicInteger index,
                                          AtomicInteger enrichedCount, AtomicInteger errorCount) {
        int currentIndex = index.getAndIncrement();
        if (currentIndex >= favorites.size()) {
            return Mono.empty();
        }

        Favorite favorite = favorites.get(currentIndex);
        log.info("Enrichissement {}/{}: {} (délai: {}ms)",
                currentIndex + 1, favorites.size(), favorite.getTitle(), enrichmentDelayMs);

        return fetchItemDetails(favorite.getVintedId())
                .delaySubscription(Duration.ofMillis(enrichmentDelayMs)) // Délai configurable pour éviter le rate limiting
                .retryWhen(reactor.util.retry.Retry.backoff(2, Duration.ofSeconds(5))
                        .filter(throwable -> throwable.getMessage() != null &&
                                (throwable.getMessage().contains("429") ||
                                 throwable.getMessage().contains("Too Many Requests")))
                        .doBeforeRetry(signal ->
                            log.warn("⚠️  Erreur 429 sur {}, retry #{} dans {}s...",
                                    favorite.getTitle(),
                                    signal.totalRetries() + 1,
                                    5 * Math.pow(2, signal.totalRetries())
                            )
                        )
                )
                .doOnNext(details -> {
                    // Mettre à jour le favori avec les détails
                    favorite.setCategory(details.getCategory());
                    favorite.setGender(details.getGender());
                    favorite.setListedDate(details.getListedDate());
                    favoriteService.saveFavorite(favorite);
                    enrichedCount.incrementAndGet();
                    log.info("✅ Favori enrichi: {} - category={}, gender={}, listedDate={}",
                            favorite.getTitle(), details.getCategory(), details.getGender(), details.getListedDate());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Article {} non disponible pour enrichissement (404 ou supprimé)", favorite.getTitle());
                    return Mono.empty();
                }))
                .onErrorResume(e -> {
                    errorCount.incrementAndGet();
                    if (e.getMessage() != null && (e.getMessage().contains("429") || e.getMessage().contains("Too Many Requests"))) {
                        log.error("❌ Rate limiting (429) après retries sur {}: {}. Arrêt de l'enrichissement pour éviter le blocage.",
                                favorite.getTitle(), e.getMessage());
                        // Arrêter complètement l'enrichissement en cas de 429 persistant
                        return Mono.error(new RuntimeException("Rate limiting (429) détecté. Attendez quelques minutes avant de relancer."));
                    } else {
                        log.warn("⚠️  Erreur enrichissement {} (continuant): {}", favorite.getTitle(), e.getMessage());
                        return Mono.empty();
                    }
                })
                .then(Mono.defer(() -> enrichNextFavorite(favorites, index, enrichedCount, errorCount)));
    }

    private void updateExistingFavorite(Favorite existing, Favorite updated) {
        existing.setPrice(updated.getPrice());
        existing.setSold(updated.getSold());
        existing.setTitle(updated.getTitle());
        existing.setImageUrl(updated.getImageUrl());
        existing.setCondition(updated.getCondition());

        // Mettre à jour category et gender si disponibles dans la mise à jour
        if (updated.getCategory() != null && existing.getCategory() == null) {
            existing.setCategory(updated.getCategory());
        }
        if (updated.getGender() != null && existing.getGender() == null) {
            existing.setGender(updated.getGender());
        }
        if (updated.getBrand() != null && existing.getBrand() == null) {
            existing.setBrand(updated.getBrand());
        }
        if (updated.getSize() != null && existing.getSize() == null) {
            existing.setSize(updated.getSize());
        }
        if (updated.getSellerName() != null && existing.getSellerName() == null) {
            existing.setSellerName(updated.getSellerName());
        }
        if (updated.getListedDate() != null && existing.getListedDate() == null) {
            existing.setListedDate(updated.getListedDate());
        }
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
