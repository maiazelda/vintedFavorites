package com.vintedFav.vintedFavorites.controller;

import com.vintedFav.vintedFavorites.dto.ExtensionSyncRequest;
import com.vintedFav.vintedFavorites.dto.SyncResponse;
import com.vintedFav.vintedFavorites.service.ExtensionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller pour recevoir les données de l'extension Chrome.
 *
 * L'extension envoie les favoris récupérés depuis le navigateur
 * de l'utilisateur, évitant ainsi d'avoir besoin de Playwright
 * côté serveur.
 */
@RestController
@RequestMapping("/api/extension")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // Permettre les requêtes depuis l'extension
public class ExtensionController {

    private final ExtensionSyncService extensionSyncService;

    /**
     * Endpoint pour recevoir les favoris et cookies depuis l'extension.
     *
     * POST /api/extension/sync
     * Body: { favorites: [...], cookies: [...] }
     */
    @PostMapping("/sync")
    public ResponseEntity<SyncResponse> syncFromExtension(@RequestBody ExtensionSyncRequest request) {
        log.info("Requête sync reçue depuis l'extension");

        try {
            SyncResponse response = extensionSyncService.syncFromExtension(request);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors de la sync extension: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new SyncResponse(false, "Erreur: " + e.getMessage(), 0, 0));
        }
    }

    /**
     * Endpoint de health check pour l'extension.
     * Permet à l'extension de vérifier que le serveur est accessible.
     *
     * GET /api/extension/health
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse(true, "Serveur disponible"));
    }

    /**
     * Réponse simple pour le health check.
     */
    public record HealthResponse(boolean ok, String message) {}
}
