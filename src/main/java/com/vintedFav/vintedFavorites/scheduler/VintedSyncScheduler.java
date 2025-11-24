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

    @Value("${vinted.cookies.initial:}")
    private String initialCookies;

    /**
     * Charge les cookies et lance la synchronisation au démarrage de l'application
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=== Démarrage de l'initialisation Vinted ===");

        // Charger les cookies initiaux si configurés
        if (initialCookies != null && !initialCookies.isEmpty()) {
            log.info("Chargement des cookies initiaux...");
            cookieService.saveAllCookiesFromRawString(initialCookies, "vinted.fr");
            log.info("Cookies chargés avec succès");
        }

        // Synchroniser au démarrage si activé
        if (syncEnabled && syncOnStartup) {
            log.info("Synchronisation au démarrage activée");
            syncFavorites();
        }
    }

    /**
     * Synchronisation périodique des favoris
     * Par défaut: toutes les 30 minutes
     */
    @Scheduled(fixedRateString = "${vinted.sync.interval:1800000}")
    public void scheduledSync() {
        if (!syncEnabled) {
            return;
        }

        log.info("=== Synchronisation périodique des favoris ===");
        syncFavorites();
    }

    private void syncFavorites() {
        if (!vintedApiService.isSessionValid()) {
            log.warn("Session non valide - synchronisation ignorée. Veuillez mettre à jour les cookies.");
            return;
        }

        vintedApiService.syncAllFavorites()
                .doOnSuccess(count -> log.info("Synchronisation terminée: {} nouveaux favoris ajoutés", count))
                .doOnError(error -> log.error("Erreur lors de la synchronisation: {}", error.getMessage()))
                .subscribe();
    }
}
