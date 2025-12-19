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

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("========================================");
        log.info("=== DÉMARRAGE VINTED FAVORITES ===");
        log.info("========================================");

        // Charger les cookies
        if (initialCookies != null && !initialCookies.isEmpty()) {
            log.info("Chargement des cookies...");
            cookieService.saveAllCookiesFromRawString(initialCookies, "vinted.fr");
            log.info("Session valide: {}", vintedApiService.isSessionValid());
        } else {
            log.warn("Aucun cookie configuré!");
        }

        // Sync + enrichissement au démarrage
        if (syncEnabled && syncOnStartup) {
            if (vintedApiService.isSessionValid()) {
                log.info("Lancement sync + enrichissement...");
                vintedApiService.syncAllFavorites()
                        .doOnSuccess(count -> log.info("=== INITIALISATION TERMINÉE ==="))
                        .doOnError(e -> log.error("Erreur: {}", e.getMessage()))
                        .subscribe();
            } else {
                log.error("Session invalide - vérifiez vos cookies");
            }
        }
    }

    @Scheduled(fixedRateString = "${vinted.sync.interval:1800000}", initialDelay = 1800000)
    public void scheduledSync() {
        if (!syncEnabled || !vintedApiService.isSessionValid()) {
            return;
        }
        log.info("=== SYNC PÉRIODIQUE ===");
        vintedApiService.syncAllFavorites().subscribe();
    }
}
