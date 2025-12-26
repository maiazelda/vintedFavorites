package com.vintedFav.vintedFavorites.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class VintedAuthService {

    private final WebClient webClient;
    private final VintedCookieService cookieService;
    private final ObjectMapper objectMapper;

    @Value("${vinted.api.base-url:https://www.vinted.fr}")
    private String baseUrl;

    @Value("${vinted.api.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36}")
    private String userAgent;

    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    /**
     * Rafraîchit le token d'accès en utilisant le refresh token
     */
    public Mono<Boolean> refreshAccessToken() {
        // Éviter les appels concurrents
        if (!refreshInProgress.compareAndSet(false, true)) {
            log.info("Refresh token déjà en cours...");
            return Mono.just(false);
        }

        Optional<String> refreshToken = cookieService.getCookieByName("refresh_token_web")
                .map(cookie -> cookie.getCookieValue());

        if (refreshToken.isEmpty()) {
            log.error("Refresh token non trouvé");
            refreshInProgress.set(false);
            return Mono.just(false);
        }

        String cookieHeader = cookieService.buildCookieHeader();
        log.info("Tentative de refresh du token d'accès...");

        return webClient.post()
                .uri(baseUrl + "/oauth/token")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br")
                .header(HttpHeaders.COOKIE, cookieHeader)
                .header(HttpHeaders.REFERER, "https://www.vinted.fr/")
                .header(HttpHeaders.ORIGIN, "https://www.vinted.fr")
                .header("Sec-Ch-Ua", "\"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Upgrade-Insecure-Requests", "1")
                .body(BodyInserters.fromFormData("grant_type", "refresh_token")
                        .with("refresh_token", refreshToken.get())
                        .with("client_id", "web"))
                .exchangeToMono(response -> {
                    // Capturer les nouveaux cookies de la réponse
                    response.headers().header(HttpHeaders.SET_COOKIE).forEach(setCookie -> {
                        log.debug("Cookie reçu lors du refresh: {}", setCookie);
                        cookieService.updateCookiesFromResponse(setCookie);
                    });

                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class)
                                .map(body -> {
                                    try {
                                        JsonNode json = objectMapper.readTree(body);
                                        String newAccessToken = json.path("access_token").asText();
                                        String newRefreshToken = json.path("refresh_token").asText();

                                        if (!newAccessToken.isEmpty()) {
                                            cookieService.saveCookie("access_token_web", newAccessToken, "vinted.fr", null);
                                            log.info("Access token rafraîchi avec succès");
                                        }
                                        if (!newRefreshToken.isEmpty()) {
                                            cookieService.saveCookie("refresh_token_web", newRefreshToken, "vinted.fr", null);
                                            log.info("Refresh token mis à jour");
                                        }
                                        return true;
                                    } catch (Exception e) {
                                        log.error("Erreur lors du parsing de la réponse de refresh: {}", e.getMessage());
                                        return false;
                                    }
                                });
                    } else {
                        log.error("Échec du refresh token: {}", response.statusCode());
                        return response.bodyToMono(String.class)
                                .doOnNext(body -> log.error("Réponse d'erreur: {}", body))
                                .map(body -> false);
                    }
                })
                .doFinally(signal -> refreshInProgress.set(false))
                .onErrorResume(e -> {
                    log.error("Erreur lors du refresh token: {}", e.getMessage());
                    refreshInProgress.set(false);
                    return Mono.just(false);
                });
    }

    /**
     * Vérifie si le token d'accès est expiré (basé sur le JWT)
     */
    public boolean isAccessTokenExpired() {
        Optional<String> accessToken = cookieService.getCookieByName("access_token_web")
                .map(cookie -> cookie.getCookieValue());

        if (accessToken.isEmpty()) {
            return true;
        }

        try {
            // Décoder le JWT pour vérifier l'expiration
            String[] parts = accessToken.get().split("\\.");
            if (parts.length >= 2) {
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                JsonNode json = objectMapper.readTree(payload);
                long exp = json.path("exp").asLong(0);
                long now = System.currentTimeMillis() / 1000;

                // Considérer le token comme expiré 5 minutes avant l'expiration réelle
                boolean expired = (exp - 300) < now;
                if (expired) {
                    log.info("Token d'accès expiré ou proche de l'expiration (exp: {}, now: {})", exp, now);
                }
                return expired;
            }
        } catch (Exception e) {
            log.warn("Impossible de vérifier l'expiration du token: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Assure que le token est valide, le rafraîchit si nécessaire
     */
    public Mono<Boolean> ensureValidToken() {
        if (isAccessTokenExpired()) {
            log.info("Token expiré, tentative de refresh...");
            return refreshAccessToken();
        }
        return Mono.just(true);
    }
}
