package com.vintedFav.vintedFavorites.controller;

import com.vintedFav.vintedFavorites.dto.CookieUpdateRequest;
import com.vintedFav.vintedFavorites.dto.CredentialsRequest;
import com.vintedFav.vintedFavorites.dto.SyncResponse;
import com.vintedFav.vintedFavorites.model.VintedCookie;
import com.vintedFav.vintedFavorites.model.VintedCredentials;
import com.vintedFav.vintedFavorites.service.FavoriteService;
import com.vintedFav.vintedFavorites.service.VintedApiService;
import com.vintedFav.vintedFavorites.service.VintedCookieService;
import com.vintedFav.vintedFavorites.service.VintedSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/vinted")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8080"})
@RequiredArgsConstructor
@Slf4j
public class VintedSyncController {

    private final VintedApiService vintedApiService;
    private final VintedCookieService cookieService;
    private final FavoriteService favoriteService;
    private final VintedSessionService sessionService;

    /**
     * Met à jour les cookies Vinted
     * Accepte soit un Map de cookies, soit une chaîne brute de cookies
     */
    @PostMapping("/cookies")
    public ResponseEntity<Map<String, Object>> updateCookies(@RequestBody CookieUpdateRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            int cookieCount = 0;

            if (request.getCookies() != null && !request.getCookies().isEmpty()) {
                cookieService.saveAllCookies(request.getCookies(), "vinted.fr");
                cookieCount = request.getCookies().size();
            } else if (request.getRawCookies() != null && !request.getRawCookies().isEmpty()) {
                Map<String, String> parsedCookies = parseRawCookies(request.getRawCookies());
                cookieService.saveAllCookies(parsedCookies, "vinted.fr");
                cookieCount = parsedCookies.size();
            }

            // Sauvegarder les headers spéciaux si fournis
            if (request.getCsrfToken() != null && !request.getCsrfToken().isEmpty()) {
                cookieService.saveCsrfToken(request.getCsrfToken());
                log.info("X-Csrf-Token configuré");
            }
            if (request.getAnonId() != null && !request.getAnonId().isEmpty()) {
                cookieService.saveAnonId(request.getAnonId());
                log.info("X-Anon-Id configuré");
            }

            if (cookieCount > 0 || request.getCsrfToken() != null || request.getAnonId() != null) {
                response.put("success", true);
                response.put("message", "Configuration mise à jour avec succès");
                response.put("cookieCount", cookieCount);
                response.put("csrfTokenSet", request.getCsrfToken() != null && !request.getCsrfToken().isEmpty());
                response.put("anonIdSet", request.getAnonId() != null && !request.getAnonId().isEmpty());
            } else {
                response.put("success", false);
                response.put("message", "Aucune donnée fournie");
            }
        } catch (Exception e) {
            log.error("Erreur lors de la mise à jour des cookies: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Erreur: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère tous les cookies actifs
     */
    @GetMapping("/cookies")
    public ResponseEntity<List<VintedCookie>> getCookies() {
        return ResponseEntity.ok(cookieService.getAllActiveCookies());
    }

    /**
     * Supprime tous les cookies
     */
    @DeleteMapping("/cookies")
    public ResponseEntity<Map<String, Object>> deleteCookies() {
        cookieService.deactivateAllCookies();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Tous les cookies ont été désactivés");
        return ResponseEntity.ok(response);
    }

    /**
     * Vérifie si la session est valide
     */
    @GetMapping("/session/status")
    public ResponseEntity<Map<String, Object>> getSessionStatus() {
        Map<String, Object> response = new HashMap<>();
        boolean isValid = vintedApiService.isSessionValid();
        response.put("valid", isValid);
        response.put("message", isValid ? "Session active" : "Session expirée ou cookies non configurés");
        return ResponseEntity.ok(response);
    }

    /**
     * Synchronise les favoris depuis Vinted
     * Si la session est expirée et que les credentials sont configurés,
     * lance automatiquement un refresh via Playwright avant de synchroniser
     */
    @PostMapping("/sync")
    public Mono<ResponseEntity<SyncResponse>> syncFavorites() {
        log.info("Démarrage de la synchronisation des favoris");

        // Si session invalide mais credentials configurés -> auto-refresh
        if (!vintedApiService.isSessionValid() && sessionService.hasCredentials()) {
            log.info("Session expirée - lancement automatique du refresh Playwright...");

            return Mono.fromFuture(sessionService.refreshSession())
                    .flatMap(success -> {
                        if (success) {
                            log.info("Refresh réussi - lancement de la synchronisation...");
                            return syncFavoritesInternal();
                        } else {
                            log.error("Échec du refresh automatique");
                            return Mono.just(ResponseEntity.badRequest()
                                    .body(new SyncResponse(false, "Échec du rafraîchissement automatique de la session", 0, 0)));
                        }
                    });
        }

        // Si session invalide et pas de credentials -> erreur
        if (!vintedApiService.isSessionValid()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(new SyncResponse(false, "Session expirée - configurez vos identifiants avec POST /api/vinted/credentials", 0, 0)));
        }

        // Session valide -> sync directement
        return syncFavoritesInternal();
    }

    private Mono<ResponseEntity<SyncResponse>> syncFavoritesInternal() {
        return vintedApiService.syncAllFavorites()
                .map(newCount -> {
                    int totalCount = favoriteService.getAllFavorites().size();
                    log.info("Synchronisation terminée: {} nouveaux, {} total", newCount, totalCount);
                    return ResponseEntity.ok(new SyncResponse(
                            true,
                            "Synchronisation réussie",
                            newCount,
                            totalCount
                    ));
                })
                .onErrorResume(e -> {
                    log.error("Erreur lors de la synchronisation: {}", e.getMessage());
                    String errorMessage = e.getMessage();
                    if (errorMessage.contains("Session expirée")) {
                        errorMessage = "Session expirée - une nouvelle tentative de refresh sera effectuée";
                    }
                    return Mono.just(ResponseEntity.badRequest()
                            .body(new SyncResponse(false, errorMessage, 0, 0)));
                });
    }

    /**
     * Récupère les favoris d'une page spécifique (sans sauvegarde)
     */
    @GetMapping("/favorites/preview")
    public Mono<ResponseEntity<?>> previewFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage) {

        if (!vintedApiService.isSessionValid()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Cookies non configurés ou session expirée");
            return Mono.just(ResponseEntity.badRequest().body(error));
        }

        return vintedApiService.fetchFavorites(page, perPage)
                .<ResponseEntity<?>>map(favorites -> ResponseEntity.ok().body(favorites))
                .onErrorResume(e -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("message", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(error));
                });
    }

    /**
     * Force l'enrichissement des favoris incomplets (sans category ou gender)
     */
    @PostMapping("/favorites/enrich")
    public Mono<ResponseEntity<Map<String, Object>>> enrichIncompleteFavorites() {
        log.info("Démarrage de l'enrichissement forcé des favoris incomplets");

        if (!vintedApiService.isSessionValid()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Cookies non configurés ou session expirée");
            return Mono.just(ResponseEntity.badRequest().body(error));
        }

        int toEnrichCount = vintedApiService.getFavoritesNeedingEnrichment().size();
        int totalCount = favoriteService.getAllFavorites().size();

        if (toEnrichCount == 0) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Tous les favoris sont déjà complets");
            response.put("enriched", 0);
            response.put("total", totalCount);
            return Mono.just(ResponseEntity.ok(response));
        }

        log.info("Enrichissement de {} favoris sur {}", toEnrichCount, totalCount);

        return vintedApiService.enrichAllUntilComplete()
                .then(Mono.fromCallable(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "Enrichissement terminé");
                    response.put("enriched", toEnrichCount);
                    response.put("total", totalCount);
                    return ResponseEntity.ok(response);
                }))
                .onErrorResume(e -> {
                    log.error("Erreur lors de l'enrichissement: {}", e.getMessage());
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("message", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(error));
                });
    }

    private Map<String, String> parseRawCookies(String rawCookies) {
        Map<String, String> cookies = new HashMap<>();
        String[] pairs = rawCookies.split(";");

        for (String pair : pairs) {
            String trimmed = pair.trim();
            int equalIndex = trimmed.indexOf('=');
            if (equalIndex > 0) {
                String name = trimmed.substring(0, equalIndex).trim();
                String value = trimmed.substring(equalIndex + 1).trim();
                cookies.put(name, value);
            }
        }

        return cookies;
    }

    // ==================== CREDENTIALS MANAGEMENT ====================

    /**
     * Configure Vinted credentials for automatic session refresh
     */
    @PostMapping("/credentials")
    public ResponseEntity<Map<String, Object>> saveCredentials(@RequestBody CredentialsRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (request.getEmail() == null || request.getEmail().isEmpty() ||
                request.getPassword() == null || request.getPassword().isEmpty()) {
                response.put("success", false);
                response.put("message", "Email et mot de passe requis");
                return ResponseEntity.badRequest().body(response);
            }

            sessionService.saveCredentials(request.getEmail(), request.getPassword(), request.getUserId());

            response.put("success", true);
            response.put("message", "Identifiants sauvegardés. Utilisez /session/refresh pour vous connecter.");
            response.put("email", request.getEmail());
            log.info("Credentials configured for: {}", request.getEmail());

        } catch (Exception e) {
            log.error("Error saving credentials: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Erreur: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Check if credentials are configured
     */
    @GetMapping("/credentials/status")
    public ResponseEntity<Map<String, Object>> getCredentialsStatus() {
        Map<String, Object> response = new HashMap<>();
        Optional<VintedCredentials> credentials = sessionService.getActiveCredentials();

        response.put("configured", credentials.isPresent());
        if (credentials.isPresent()) {
            response.put("email", credentials.get().getEmail());
            response.put("lastRefresh", credentials.get().getLastRefresh());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Delete saved credentials
     */
    @DeleteMapping("/credentials")
    public ResponseEntity<Map<String, Object>> deleteCredentials() {
        sessionService.deleteAllCredentials();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Identifiants supprimés");
        return ResponseEntity.ok(response);
    }

    // ==================== SESSION MANAGEMENT ====================

    /**
     * Manually trigger a session refresh using Playwright
     * This will open a browser, login to Vinted, and update cookies automatically
     */
    @PostMapping("/session/refresh")
    public ResponseEntity<Map<String, Object>> refreshSession() {
        Map<String, Object> response = new HashMap<>();

        if (!sessionService.hasCredentials()) {
            response.put("success", false);
            response.put("message", "Aucun identifiant configuré. Utilisez POST /credentials d'abord.");
            return ResponseEntity.badRequest().body(response);
        }

        if (sessionService.isRefreshInProgress()) {
            response.put("success", false);
            response.put("message", "Un rafraîchissement est déjà en cours...");
            return ResponseEntity.ok(response);
        }

        log.info("Starting manual session refresh...");

        // Start async refresh
        sessionService.refreshSession()
                .thenAccept(success -> {
                    if (success) {
                        log.info("Session refresh completed successfully");
                    } else {
                        log.error("Session refresh failed");
                    }
                });

        response.put("success", true);
        response.put("message", "Rafraîchissement de session démarré. Vérifiez les logs pour le statut.");
        response.put("inProgress", true);

        return ResponseEntity.ok(response);
    }

    /**
     * Get the current refresh status
     */
    @GetMapping("/session/refresh/status")
    public ResponseEntity<Map<String, Object>> getRefreshStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("inProgress", sessionService.isRefreshInProgress());
        response.put("hasCredentials", sessionService.hasCredentials());
        response.put("sessionValid", vintedApiService.isSessionValid());
        return ResponseEntity.ok(response);
    }
}
