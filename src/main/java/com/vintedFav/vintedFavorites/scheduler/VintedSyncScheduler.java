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
     * Au démarrage: charge les cookies, synchronise et enrichit TOUS les favoris incomplets
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("========================================");
        log.info("=== INITIALISATION VINTED FAVORITES ===");
        log.info("========================================");

        // Charger les cookies initiaux
        if (initialCookies != null && !initialCookies.isEmpty()) {
            log.info("Chargement des cookies initiaux...");
            cookieService.saveAllCookiesFromRawString(initialCookies, "vinted.fr");
            log.info("Cookies chargés. Session valide: {}", vintedApiService.isSessionValid());
        } else {
            log.warn("Aucun cookie initial configuré dans application.properties");
        }

        // Synchronisation + enrichissement complet au démarrage
        if (syncEnabled && syncOnStartup) {
            if (vintedApiService.isSessionValid()) {
                log.info("Démarrage de la synchronisation...");
                syncAndEnrichAll();
            } else {
                log.error("Session non valide - impossible de synchroniser. Vérifiez vos cookies.");
            }
        } else {
            log.info("Synchronisation au démarrage désactivée (sync.enabled={}, sync.on-startup={})",
                    syncEnabled, syncOnStartup);
        }
    }

    /**
     * Synchronisation périodique (par défaut: toutes les 30 minutes)
     */
    @Scheduled(fixedRateString = "${vinted.sync.interval:1800000}", initialDelay = 1800000)
    public void scheduledSync() {
        if (!syncEnabled) {
            return;
        }

        log.info("=== Synchronisation périodique ===");
        if (vintedApiService.isSessionValid()) {
            syncAndEnrichAll();
        } else {
            log.warn("Session non valide - synchronisation périodique ignorée");
        }
    }

    /**
     * Synchronise les favoris puis enrichit TOUS les favoris incomplets (en boucle)
     */
    private void syncAndEnrichAll() {
        vintedApiService.syncAllFavorites()
                .doOnSuccess(count -> {
                    log.info("Synchronisation terminée: {} nouveaux favoris", count);

                    // Enrichissement complet en boucle
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
            log.info("✓ Tous les favoris sont complets (category + gender renseignés)");
            return;
        }

        log.info("Enrichissement: {} favoris incomplets à traiter...", toEnrich);

        vintedApiService.enrichAllIncompleteFavorites()
                .doOnTerminate(() -> {
                    int remaining = vintedApiService.getFavoritesNeedingEnrichment().size();
                    if (remaining > 0) {
                        log.info("Pause de 5s avant le prochain batch ({} restants)...", remaining);
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("Enrichissement interrompu");
                            return;
                        }
                        enrichAllUntilComplete();
                    } else {
                        log.info("========================================");
                        log.info("✓ ENRICHISSEMENT COMPLET TERMINÉ");
                        log.info("========================================");
                    }
                })
                .subscribe();
    }
}
