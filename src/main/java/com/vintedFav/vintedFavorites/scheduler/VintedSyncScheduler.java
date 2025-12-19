package com.vintedFav.vintedFavorites.scheduler;

import com.vintedFav.vintedFavorites.service.VintedApiService;
import com.vintedFav.vintedFavorites.service.VintedCookieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class VintedSyncScheduler {

    private final VintedApiService vintedApiService;
    private final VintedCookieService cookieService;

    @Value("${vinted.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${vinted.sync.on-startup:true}")
    private boolean syncOnStartup;

    @Value("${vinted.sync.enrich-on-startup:true}")
    private boolean enrichOnStartup;

    @Value("${vinted.cookies.initial:}")
    private String initialCookies;

    @Value("${vinted.api.enrichment-delay:2000}")
    private int enrichmentDelayMs;

    /**
     * Au démarrage: charge les cookies, synchronise et enrichit TOUS les favoris incomplets
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=== Initialisation Vinted ===");

        // Charger les cookies initiaux
        if (initialCookies != null && !initialCookies.isEmpty()) {
            log.info("Chargement des cookies...");
            cookieService.saveAllCookiesFromRawString(initialCookies, "vinted.fr");
        }

        // Synchronisation + enrichissement complet au démarrage
        if (syncEnabled && syncOnStartup) {
            log.info("Synchronisation au démarrage...");
            syncAndEnrichAll();
        }
    }

    /**
     * Synchronisation périodique (par défaut: toutes les 30 minutes)
     */
    @Scheduled(fixedRateString = "${vinted.sync.interval:1800000}")
    public void scheduledSync() {
        if (!syncEnabled) {
            return;
        }

        log.info("=== Synchronisation périodique ===");
        syncAndEnrichAll();
    }

    /**
     * Synchronise les favoris puis enrichit TOUS les favoris incomplets (en boucle)
     */
    private void syncAndEnrichAll() {
        if (!vintedApiService.isSessionValid()) {
            log.warn("Session non valide - synchronisation ignorée");
            return;
        }

        vintedApiService.syncAllFavorites()
                .doOnSuccess(count -> {
                    log.info("Sync terminée: {} nouveaux favoris", count);

                    // Enrichissement complet: boucle tant qu'il y a des favoris incomplets
                    if (enrichOnStartup) {
                        enrichAllUntilComplete();
                    }
                })
                .doOnError(error -> log.error("Erreur synchronisation: {}", error.getMessage()))
                .subscribe();
    }

    /**
     * Enrichit les favoris en boucle jusqu'à ce qu'il n'y en ait plus à enrichir
     */
    private void enrichAllUntilComplete() {
        int toEnrich = vintedApiService.getFavoritesNeedingEnrichment().size();

        if (toEnrich == 0) {
            log.info("✓ Tous les favoris sont complets");
            return;
        }

        log.info("Démarrage enrichissement de {} favoris incomplets...", toEnrich);

        vintedApiService.enrichAllIncompleteFavorites()
                .doOnTerminate(() -> {
                    // Vérifier s'il reste des favoris à enrichir
                    int remaining = vintedApiService.getFavoritesNeedingEnrichment().size();
                    if (remaining > 0) {
                        log.info("Pause de 5s avant le prochain batch ({} favoris restants)...", remaining);
                        // Attendre 5 secondes puis relancer
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        enrichAllUntilComplete(); // Récursion
                    } else {
                        log.info("✓ Enrichissement complet terminé - tous les favoris sont à jour");
                    }
                })
                .subscribe();
    }
}
