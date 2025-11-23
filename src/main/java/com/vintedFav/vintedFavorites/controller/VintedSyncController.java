package com.vintedFav.vintedFavorites.controller;

import com.vintedFav.vintedFavorites.dto.CookieUpdateRequest;
import com.vintedFav.vintedFavorites.dto.SyncResponse;
import com.vintedFav.vintedFavorites.model.VintedCookie;
import com.vintedFav.vintedFavorites.service.FavoriteService;
import com.vintedFav.vintedFavorites.service.VintedApiService;
import com.vintedFav.vintedFavorites.service.VintedCookieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vinted")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
@Slf4j
public class VintedSyncController {

    private final VintedApiService vintedApiService;
    private final VintedCookieService cookieService;
    private final FavoriteService favoriteService;

    /**
     * Met à jour les cookies Vinted
     * Accepte soit un Map de cookies, soit une chaîne brute de cookies
     */
    @PostMapping("/cookies")
    public ResponseEntity<Map<String, Object>> updateCookies(@RequestBody CookieUpdateRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (request.getCookies() != null && !request.getCookies().isEmpty()) {
                cookieService.saveAllCookies(request.getCookies(), "vinted.fr");
                response.put("success", true);
                response.put("message", "Cookies mis à jour avec succès");
                response.put("count", request.getCookies().size());
            } else if (request.getRawCookies() != null && !request.getRawCookies().isEmpty()) {
                Map<String, String> parsedCookies = parseRawCookies(request.getRawCookies());
                cookieService.saveAllCookies(parsedCookies, "vinted.fr");
                response.put("success", true);
                response.put("message", "Cookies mis à jour avec succès");
                response.put("count", parsedCookies.size());
            } else {
                response.put("success", false);
                response.put("message", "Aucun cookie fourni");
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
     */
    @PostMapping("/sync")
    public Mono<ResponseEntity<SyncResponse>> syncFavorites() {
        log.info("Démarrage de la synchronisation des favoris");

        if (!vintedApiService.isSessionValid()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(new SyncResponse(false, "Cookies non configurés ou session expirée", 0, 0)));
        }

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
                        errorMessage = "Session expirée - veuillez mettre à jour les cookies";
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
}
