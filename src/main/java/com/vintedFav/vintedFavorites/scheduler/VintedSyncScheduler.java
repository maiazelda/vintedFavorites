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

    @Value("${vinted.credentials.email:}")
    private String envEmail;

    @Value("${vinted.credentials.password:}")
    private String envPassword;

    @Value("${vinted.api.user-id:}")
    private String userId;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("========================================");
        log.info("=== DÉMARRAGE VINTED FAVORITES ===");
        log.info("========================================");

        // Charger les credentials depuis les variables d'environnement si pas déjà configurés
        loadCredentialsFromEnv();

        // Charger les cookies
        if (initialCookies != null && !initialCookies.isEmpty()) {
            log.info("Chargement des cookies...");
            cookieService.saveAllCookiesFromRawString(initialCookies, "vinted.fr");
        }

        if (!syncEnabled || !syncOnStartup) {
            log.info("Sync au démarrage désactivé");
            return;
        }

        // Vérifier si on a des cookies valides
        if (vintedApiService.isSessionValid()) {
            // Session valide, lancer la sync directement
            startSync();
        } else if (sessionService.hasCredentials()) {
            // Pas de cookies valides mais on a des credentials -> login automatique
            log.info("Session invalide - tentative de login automatique via Playwright...");
            sessionService.refreshSession()
                    .thenAccept(success -> {
                        if (success) {
                            log.info("Login automatique réussi !");
                            startSync();
                        } else {
                            log.error("Échec du login automatique - vérifiez vos identifiants");
                        }
                    });
        } else {
            log.warn("Aucune méthode d'authentification configurée !");
            log.warn("Configurez VINTED_EMAIL + VINTED_PASSWORD ou VINTED_COOKIES dans .env");
        }
    }

    private void loadCredentialsFromEnv() {
        // Charger les credentials depuis les variables d'environnement si configurés
        if (envEmail != null && !envEmail.isEmpty() &&
            envPassword != null && !envPassword.isEmpty()) {

            if (!sessionService.hasCredentials()) {
                log.info("Chargement des credentials depuis les variables d'environnement...");
                sessionService.saveCredentials(envEmail, envPassword, userId);
                log.info("Credentials configurés pour: {}", envEmail);
            } else {
                log.debug("Credentials déjà configurés en base de données");
            }
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
