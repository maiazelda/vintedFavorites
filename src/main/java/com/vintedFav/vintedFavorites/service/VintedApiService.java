package com.vintedFav.vintedFavorites.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vintedFav.vintedFavorites.model.Favorite;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
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

    @Value("${vinted.api.max-enrichment-batch:50}")
    private int maxEnrichmentBatch;

    // ==================== FETCH FAVORITES ====================

    public Mono<List<Favorite>> fetchFavorites(int page, int perPage) {
        return authService.ensureValidToken()
                .flatMap(valid -> fetchFavoritesInternal(page, perPage, false));
    }

    private Mono<List<Favorite>> fetchFavoritesInternal(int page, int perPage, boolean isRetry) {
        String cookieHeader = cookieService.buildCookieHeader();

        if (cookieHeader.isEmpty()) {
            log.warn("Aucun cookie actif trouvé");
            return Mono.error(new RuntimeException("Cookies non configurés"));
        }

        if (userId == null || userId.isEmpty()) {
            log.error("User ID non configuré");
            return Mono.error(new RuntimeException("User ID non configuré"));
        }

        String url = baseUrl + "/api/v2/users/" + userId + "/items/favourites?page=" + page + "&per_page=" + perPage;
        log.debug("Appel API Vinted: {}", url);

        return buildRequest(url, cookieHeader)
                .exchangeToMono(this::handleResponse)
                .map(this::parseFavoritesResponse)
                .onErrorResume(e -> {
                    if (!isRetry && e.getMessage() != null && e.getMessage().contains("401")) {
                        log.warn("Erreur 401 - Tentative de refresh token...");
                        return authService.refreshAccessToken()
                                .flatMap(success -> success ? fetchFavoritesInternal(page, perPage, true) : Mono.error(e));
                    }
                    return Mono.error(e);
                });
    }

    // ==================== FETCH ITEM DETAILS (HTML SCRAPING) ====================

    public Mono<Favorite> fetchItemDetails(String itemId) {
        return authService.ensureValidToken()
                .flatMap(valid -> fetchItemDetailsFromHtml(itemId));
    }

    private Mono<Favorite> fetchItemDetailsFromHtml(String itemId) {
        String cookieHeader = cookieService.buildCookieHeader();
        String url = baseUrl + "/items/" + itemId;

        return webClient.get()
                .uri(url)
                .header(HttpHeaders.COOKIE, cookieHeader)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "fr-FR,fr;q=0.9")
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class);
                    }
                    log.debug("Erreur {} pour item {}", response.statusCode(), itemId);
                    return Mono.empty();
                })
                .flatMap(html -> parseItemFromHtml(html, itemId))
                .onErrorResume(e -> {
                    log.debug("Erreur scraping pour item {}: {}", itemId, e.getMessage());
                    return Mono.empty();
                });
    }

    // ==================== HTML PARSING ====================

    private Mono<Favorite> parseItemFromHtml(String html, String itemId) {
        try {
            Favorite favorite = new Favorite();
            favorite.setVintedId(itemId);

            String gender = extractGenderFromBreadcrumb(html);
            String category = extractCategoryFromBreadcrumb(html);

            if (gender != null) favorite.setGender(gender);
            if (category != null) favorite.setCategory(category);

            if (favorite.getGender() != null || favorite.getCategory() != null) {
                return Mono.just(favorite);
            }

            log.debug("Impossible d'extraire category/gender pour item {}", itemId);
            return Mono.empty();

        } catch (Exception e) {
            log.debug("Erreur parsing HTML pour item {}: {}", itemId, e.getMessage());
            return Mono.empty();
        }
    }

    private String extractGenderFromBreadcrumb(String html) {
        String searchArea = html.substring(0, Math.min(html.length(), 100000)).toLowerCase();

        if (searchArea.contains("/hommes") || searchArea.contains(">hommes<")) {
            return "Homme";
        }
        if (searchArea.contains("/femmes") || searchArea.contains(">femmes<")) {
            return "Femme";
        }
        if (searchArea.contains("/enfants") || searchArea.contains(">enfants<")) {
            return "Enfant";
        }
        return null;
    }

    private String extractCategoryFromBreadcrumb(String html) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "href=\"/[^\"]*?/([^/\"]+)\"[^>]*>([^<]+)</a>\\s*(?:/|$)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );

        java.util.List<String> categories = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String linkText = matcher.group(2).trim();
            if (!linkText.equalsIgnoreCase("Accueil") &&
                !linkText.equalsIgnoreCase("Hommes") &&
                !linkText.equalsIgnoreCase("Femmes") &&
                !linkText.equalsIgnoreCase("Enfants") &&
                !linkText.isEmpty()) {
                categories.add(linkText);
            }
        }

        if (categories.isEmpty()) {
            String[] commonCategories = {
                "Pantalons", "Pantalon", "Robes", "Robe", "Chemises", "Chemise",
                "T-shirts", "T-shirt", "Vestes", "Veste", "Manteaux", "Manteau",
                "Chaussures", "Pulls", "Pull", "Jeans", "Jean", "Shorts", "Short",
                "Jupes", "Jupe", "Blazers", "Blazer", "Sweats", "Sweat",
                "Accessoires", "Sacs", "Sac", "Bijoux", "Montres"
            };

            for (String cat : commonCategories) {
                if (html.contains(">" + cat + "<") || html.contains(">" + cat + " ")) {
                    return cat;
                }
            }
        }

        return categories.isEmpty() ? null : categories.get(categories.size() - 1);
    }

    // ==================== SYNC & ENRICHMENT ====================

    public Mono<Integer> syncAllFavorites() {
        log.info("Démarrage synchronisation des favoris...");
        return fetchAllFavoritesPages()
                .flatMap(favorites -> {
                    int savedCount = 0;

                    for (Favorite favorite : favorites) {
                        try {
                            var existing = favoriteService.getFavoritesByVintedId(favorite.getVintedId());
                            if (existing.isEmpty()) {
                                favoriteService.saveFavorite(favorite);
                                savedCount++;
                                log.debug("Nouveau favori: {}", favorite.getTitle());
                            } else {
                                Favorite existingFavorite = existing.get(0);
                                updateExistingFavorite(existingFavorite, favorite);
                                favoriteService.saveFavorite(existingFavorite);
                            }
                        } catch (Exception e) {
                            log.error("Erreur sauvegarde favori: {}", e.getMessage());
                        }
                    }

                    log.info("Synchronisation terminée: {} nouveaux favoris sur {} total", savedCount, favorites.size());
                    return Mono.just(savedCount);
                });
    }

    /**
     * Récupère tous les favoris nécessitant un enrichissement depuis la BDD
     */
    public List<Favorite> getFavoritesNeedingEnrichment() {
        return favoriteService.getAllFavorites().stream()
                .filter(f -> f.getCategory() == null || f.getGender() == null)
                .toList();
    }

    /**
     * Enrichit tous les favoris incomplets (sans category ou gender)
     */
    public Mono<Void> enrichAllIncompleteFavorites() {
        List<Favorite> toEnrich = getFavoritesNeedingEnrichment();
        if (toEnrich.isEmpty()) {
            log.info("Aucun favori à enrichir");
            return Mono.empty();
        }
        log.info("Enrichissement de {} favoris incomplets...", toEnrich.size());
        return enrichFavorites(toEnrich);
    }

    public Mono<Void> enrichFavorites(List<Favorite> favorites) {
        if (favorites.isEmpty()) {
            return Mono.empty();
        }

        List<Favorite> batch = favorites.size() > maxEnrichmentBatch
                ? favorites.subList(0, maxEnrichmentBatch)
                : favorites;

        if (favorites.size() > maxEnrichmentBatch) {
            log.info("Enrichissement par batch: {}/{} favoris", maxEnrichmentBatch, favorites.size());
        }

        AtomicInteger enrichedCount = new AtomicInteger(0);
        AtomicInteger index = new AtomicInteger(0);

        return Mono.defer(() -> enrichNextFavorite(batch, index, enrichedCount))
                .doOnTerminate(() -> {
                    log.info("Enrichissement terminé: {}/{} favoris enrichis", enrichedCount.get(), batch.size());
                    int remaining = favorites.size() - maxEnrichmentBatch;
                    if (remaining > 0) {
                        log.info("{} favoris restants à enrichir", remaining);
                    }
                });
    }

    private Mono<Void> enrichNextFavorite(List<Favorite> favorites, AtomicInteger index, AtomicInteger enrichedCount) {
        int currentIndex = index.getAndIncrement();
        if (currentIndex >= favorites.size()) {
            return Mono.empty();
        }

        Favorite favorite = favorites.get(currentIndex);
        log.debug("Enrichissement {}/{}: {}", currentIndex + 1, favorites.size(), favorite.getTitle());

        return fetchItemDetails(favorite.getVintedId())
                .delaySubscription(Duration.ofMillis(enrichmentDelayMs))
                .doOnNext(details -> {
                    if (details.getCategory() != null) favorite.setCategory(details.getCategory());
                    if (details.getGender() != null) favorite.setGender(details.getGender());
                    favoriteService.saveFavorite(favorite);
                    enrichedCount.incrementAndGet();
                    log.info("Enrichi: {} -> category={}, gender={}", favorite.getTitle(), favorite.getCategory(), favorite.getGender());
                })
                .onErrorResume(e -> {
                    log.debug("Erreur enrichissement {}: {}", favorite.getTitle(), e.getMessage());
                    return Mono.empty();
                })
                .then(Mono.defer(() -> enrichNextFavorite(favorites, index, enrichedCount)));
    }

    // ==================== HELPERS ====================

    private WebClient.RequestHeadersSpec<?> buildRequest(String url, String cookieHeader) {
        String csrfToken = cookieService.getCsrfToken();
        String anonId = cookieService.getAnonId();

        var requestSpec = webClient.get()
                .uri(url)
                .header(HttpHeaders.COOKIE, cookieHeader)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .header(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .header("Accept-Language", "fr-FR,fr;q=0.9")
                .header(HttpHeaders.REFERER, "https://www.vinted.fr/")
                .header(HttpHeaders.ORIGIN, "https://www.vinted.fr");

        if (csrfToken != null && !csrfToken.isEmpty()) {
            requestSpec = requestSpec.header("X-Csrf-Token", csrfToken);
        }
        if (anonId != null && !anonId.isEmpty()) {
            requestSpec = requestSpec.header("X-Anon-Id", anonId);
        }

        return requestSpec;
    }

    private Mono<String> handleResponse(ClientResponse response) {
        response.headers().header(HttpHeaders.SET_COOKIE).forEach(setCookie -> {
            cookieService.updateCookiesFromResponse(setCookie);
        });

        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(String.class);
        } else if (response.statusCode().value() == 401 || response.statusCode().value() == 403) {
            int statusCode = response.statusCode().value();
            log.error("Session expirée ({})", statusCode);

            if (sessionService != null && sessionService.hasCredentials() && !sessionService.isRefreshInProgress()) {
                sessionService.refreshSession();
            }

            return Mono.error(new RuntimeException("Erreur " + statusCode + " - Session expirée"));
        } else {
            log.error("Erreur API Vinted: {}", response.statusCode());
            return response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new RuntimeException("Erreur API: " + response.statusCode())));
        }
    }

    private List<Favorite> parseFavoritesResponse(String responseBody) {
        List<Favorite> favorites = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode items = root.path("items");
            if (items.isMissingNode() || !items.isArray()) {
                items = root.path("favourite_items");
            }

            if (items.isArray()) {
                for (JsonNode itemWrapper : items) {
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
            log.debug("Favoris récupérés: {}", favorites.size());
        } catch (Exception e) {
            log.error("Erreur parsing réponse: {}", e.getMessage());
        }

        return favorites;
    }

    private Favorite mapJsonToFavorite(JsonNode item) {
        if (item == null || item.isMissingNode()) {
            return null;
        }

        Favorite favorite = new Favorite();

        JsonNode idNode = item.path("id");
        if (!idNode.isMissingNode()) {
            favorite.setVintedId(idNode.isNumber() ? String.valueOf(idNode.asLong()) : idNode.asText());
        }

        favorite.setTitle(getTextValue(item, "title"));
        favorite.setBrand(getTextValue(item, "brand_title"));

        JsonNode priceNode = item.path("price");
        if (!priceNode.isMissingNode()) {
            String priceStr = getTextValue(priceNode, "amount");
            if (priceStr != null) {
                try {
                    favorite.setPrice(Double.parseDouble(priceStr.replace(",", ".")));
                } catch (NumberFormatException ignored) {}
            }
        }

        JsonNode photo = item.path("photo");
        if (!photo.isMissingNode()) {
            String imageUrl = getTextValue(photo, "url");
            if (imageUrl == null) imageUrl = getTextValue(photo, "full_size_url");
            favorite.setImageUrl(imageUrl);
        }

        favorite.setProductUrl(getTextValue(item, "url"));
        favorite.setSold(item.path("is_closed").asBoolean(false));

        JsonNode user = item.path("user");
        if (!user.isMissingNode()) {
            favorite.setSellerName(getTextValue(user, "login"));
        }

        String size = getTextValue(item, "size_title");
        if (size == null) size = getTextValue(item, "size");
        favorite.setSize(size);

        favorite.setCondition(getTextValue(item, "status"));

        return favorite;
    }

    private void updateExistingFavorite(Favorite existing, Favorite updated) {
        existing.setPrice(updated.getPrice());
        existing.setSold(updated.getSold());
        existing.setTitle(updated.getTitle());
        existing.setImageUrl(updated.getImageUrl());
        existing.setCondition(updated.getCondition());

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
    }

    private String getTextValue(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asText();
    }

    private Mono<List<Favorite>> fetchAllFavoritesPages() {
        return fetchFavoritesRecursively(1, 20, new ArrayList<>());
    }

    private Mono<List<Favorite>> fetchFavoritesRecursively(int page, int perPage, List<Favorite> accumulated) {
        return fetchFavorites(page, perPage)
                .flatMap(favorites -> {
                    accumulated.addAll(favorites);
                    if (favorites.size() < perPage) {
                        return Mono.just(accumulated);
                    } else {
                        return fetchFavoritesRecursively(page + 1, perPage, accumulated);
                    }
                });
    }

    public boolean isSessionValid() {
        return cookieService.hasValidSession();
    }
}
