package com.vintedFav.vintedFavorites.scheduler;

import com.vintedFav.vintedFavorites.service.VintedApiService;
import com.vintedFav.vintedFavorites.service.VintedAuthService;
import com.vintedFav.vintedFavorites.service.VintedCookieService;
import com.vintedFav.vintedFavorites.service.VintedSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class VintedSyncScheduler {

    private final VintedApiService vintedApiService;
    private final VintedCookieService cookieService;
    private final VintedAuthService authService;
    private final VintedSessionService sessionService;

    @Value("${vinted.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${vinted.sync.on-startup:true}")
    private boolean syncOnStartup;

    @Value("${vinted.cookies.initial:}")
    private String initialCookies;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("========================================");
        log.info("=== DÉMARRAGE VINTED FAVORITES ===");
        log.info("========================================");

        // Charger les cookies
        if (initialCookies != null && !initialCookies.isEmpty()) {
            log.info("Chargement des cookies...");
            cookieService.saveAllCookiesFromRawString(initialCookies, "vinted.fr");
        } else {
            log.warn("Aucun cookie configuré!");
        }

        if (!syncEnabled || !syncOnStartup) {
            log.info("Sync au démarrage désactivé");
            return;
        }

        // Vérifier si le token est expiré
        if (authService.isAccessTokenExpired()) {
            log.warn("Token expiré - tentative de refresh via Playwright...");

            if (sessionService.hasCredentials()) {
                // Attendre que le refresh de session soit terminé avant de sync
                sessionService.refreshSession()
                        .thenAccept(success -> {
                            if (success) {
                                log.info("Session rafraîchie avec succès");
                                startSync();
                            } else {
                                log.error("Échec du refresh de session - mettez à jour vos cookies manuellement");
                            }
                        });
            } else {
                log.error("Pas de credentials configurés pour le refresh automatique");
                log.error("Utilisez POST /api/vinted/credentials pour configurer email/password");
            }
        } else {
            // Token valide, lancer la sync directement
            startSync();
        }
    }

    private void startSync() {
        if (!vintedApiService.isSessionValid()) {
            log.error("Session invalide - vérifiez vos cookies");
            return;
        }

        log.info("Lancement sync + enrichissement...");
        vintedApiService.syncAllFavorites()
                .doOnSuccess(count -> log.info("=== INITIALISATION TERMINÉE: {} favoris ===", count))
                .doOnError(e -> log.error("Erreur sync: {}", e.getMessage()))
                .subscribe();
    }

    @Scheduled(fixedRateString = "${vinted.sync.interval:1800000}", initialDelay = 1800000)
    public void scheduledSync() {
        if (!syncEnabled) {
            return;
        }

        // Vérifier et rafraîchir le token si nécessaire
        if (authService.isAccessTokenExpired() && sessionService.hasCredentials()) {
            log.info("Token expiré - refresh avant sync périodique...");
            sessionService.refreshSession()
                    .thenAccept(success -> {
                        if (success) {
                            performSync();
                        }
                    });
        } else if (vintedApiService.isSessionValid()) {
            performSync();
        }
    }

    private void performSync() {
        log.info("=== SYNC PÉRIODIQUE ===");
        vintedApiService.syncAllFavorites().subscribe();
    }
}
