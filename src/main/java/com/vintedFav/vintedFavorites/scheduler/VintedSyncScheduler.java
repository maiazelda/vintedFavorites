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

    /**
     * Au démarrage: charge les cookies, synchronise les favoris et enrichit les incomplets
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=== Initialisation Vinted ===");

        // Charger les cookies initiaux
        if (initialCookies != null && !initialCookies.isEmpty()) {
            log.info("Chargement des cookies...");
            cookieService.saveAllCookiesFromRawString(initialCookies, "vinted.fr");
        }

        // Synchronisation + enrichissement au démarrage
        if (syncEnabled && syncOnStartup) {
            log.info("Synchronisation au démarrage...");
            syncAndEnrich();
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
        syncAndEnrich();
    }

    /**
     * Synchronise les favoris puis enrichit les incomplets
     */
    private void syncAndEnrich() {
        if (!vintedApiService.isSessionValid()) {
            log.warn("Session non valide - synchronisation ignorée");
            return;
        }

        vintedApiService.syncAllFavorites()
                .doOnSuccess(count -> {
                    log.info("Sync terminée: {} nouveaux favoris", count);

                    // Enrichissement automatique après sync
                    if (enrichOnStartup) {
                        int toEnrich = vintedApiService.getFavoritesNeedingEnrichment().size();
                        if (toEnrich > 0) {
                            log.info("Démarrage enrichissement de {} favoris incomplets...", toEnrich);
                            vintedApiService.enrichAllIncompleteFavorites()
                                    .doOnTerminate(() -> log.info("Enrichissement automatique terminé"))
                                    .subscribe();
                        }
                    }
                })
                .doOnError(error -> log.error("Erreur synchronisation: {}", error.getMessage()))
                .subscribe();
    }
}
