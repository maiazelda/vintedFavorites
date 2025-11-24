package com.vintedFav.vintedFavorites.service;

import com.vintedFav.vintedFavorites.model.VintedCookie;
import com.vintedFav.vintedFavorites.repository.VintedCookieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VintedCookieService {

    private final VintedCookieRepository cookieRepository;

    // Noms réservés pour stocker les headers spéciaux
    private static final String CSRF_TOKEN_KEY = "__x_csrf_token";
    private static final String ANON_ID_KEY = "__x_anon_id";

    public List<VintedCookie> getAllActiveCookies() {
        return cookieRepository.findByIsActiveTrue()
                .stream()
                .filter(cookie -> !cookie.isExpired())
                .collect(Collectors.toList());
    }

    public Optional<VintedCookie> getCookieByName(String name) {
        return cookieRepository.findByCookieName(name);
    }

    @Transactional
    public VintedCookie saveCookie(String name, String value, String domain, LocalDateTime expiresAt) {
        Optional<VintedCookie> existingCookie = cookieRepository.findByCookieName(name);

        VintedCookie cookie;
        if (existingCookie.isPresent()) {
            cookie = existingCookie.get();
            cookie.setCookieValue(value);
            cookie.setDomain(domain);
            cookie.setExpiresAt(expiresAt);
            cookie.setIsActive(true);
            log.info("Mise à jour du cookie: {}", name);
        } else {
            cookie = new VintedCookie();
            cookie.setCookieName(name);
            cookie.setCookieValue(value);
            cookie.setDomain(domain);
            cookie.setPath("/");
            cookie.setExpiresAt(expiresAt);
            cookie.setIsActive(true);
            log.info("Création d'un nouveau cookie: {}", name);
        }

        return cookieRepository.save(cookie);
    }

    @Transactional
    public void saveAllCookies(Map<String, String> cookies, String domain) {
        cookies.forEach((name, value) -> saveCookie(name, value, domain, null));
    }

    @Transactional
    public void saveAllCookiesFromRawString(String rawCookies, String domain) {
        if (rawCookies == null || rawCookies.isEmpty()) {
            return;
        }

        String[] pairs = rawCookies.split(";");
        int count = 0;

        for (String pair : pairs) {
            String trimmed = pair.trim();
            int equalIndex = trimmed.indexOf('=');
            if (equalIndex > 0) {
                String name = trimmed.substring(0, equalIndex).trim();
                String value = trimmed.substring(equalIndex + 1).trim();
                saveCookie(name, value, domain, null);
                count++;
            }
        }

        log.info("Chargé {} cookies depuis la chaîne brute", count);
    }

    @Transactional
    public void updateCookiesFromResponse(String setCookieHeader) {
        if (setCookieHeader == null || setCookieHeader.isEmpty()) {
            return;
        }

        String[] cookieParts = setCookieHeader.split(";");
        if (cookieParts.length > 0) {
            String[] nameValue = cookieParts[0].split("=", 2);
            if (nameValue.length == 2) {
                String name = nameValue[0].trim();
                String value = nameValue[1].trim();

                LocalDateTime expiresAt = null;
                String domain = "vinted.fr";

                for (String part : cookieParts) {
                    String trimmedPart = part.trim().toLowerCase();
                    if (trimmedPart.startsWith("expires=")) {
                        // Parse expires si nécessaire
                    } else if (trimmedPart.startsWith("domain=")) {
                        domain = part.split("=", 2)[1].trim();
                    } else if (trimmedPart.startsWith("max-age=")) {
                        try {
                            long maxAge = Long.parseLong(part.split("=", 2)[1].trim());
                            expiresAt = LocalDateTime.now().plusSeconds(maxAge);
                        } catch (NumberFormatException e) {
                            log.warn("Impossible de parser max-age: {}", part);
                        }
                    }
                }

                saveCookie(name, value, domain, expiresAt);
            }
        }
    }

    public String buildCookieHeader() {
        List<VintedCookie> activeCookies = getAllActiveCookies();
        if (activeCookies.isEmpty()) {
            return "";
        }

        return activeCookies.stream()
                .map(cookie -> cookie.getCookieName() + "=" + cookie.getCookieValue())
                .collect(Collectors.joining("; "));
    }

    @Transactional
    public void deactivateCookie(String name) {
        cookieRepository.deactivateByCookieName(name);
        log.info("Cookie désactivé: {}", name);
    }

    @Transactional
    public void deactivateAllCookies() {
        cookieRepository.deactivateAll();
        log.info("Tous les cookies ont été désactivés");
    }

    @Transactional
    public void deleteCookie(String name) {
        cookieRepository.deleteByCookieName(name);
        log.info("Cookie supprimé: {}", name);
    }

    public boolean hasValidSession() {
        List<VintedCookie> activeCookies = getAllActiveCookies();
        // Vérifier si les cookies essentiels sont présents
        return activeCookies.stream()
                .anyMatch(cookie -> cookie.getCookieName().equals("_vinted_fr_session")
                        || cookie.getCookieName().equals("access_token_web"));
    }

    /**
     * Sauvegarde le X-Csrf-Token
     */
    @Transactional
    public void saveCsrfToken(String csrfToken) {
        if (csrfToken != null && !csrfToken.isEmpty()) {
            saveCookie(CSRF_TOKEN_KEY, csrfToken, "vinted.fr", null);
            log.info("X-Csrf-Token sauvegardé");
        }
    }

    /**
     * Récupère le X-Csrf-Token
     */
    public String getCsrfToken() {
        return getCookieByName(CSRF_TOKEN_KEY)
                .filter(c -> c.getIsActive() && !c.isExpired())
                .map(VintedCookie::getCookieValue)
                .orElse(null);
    }

    /**
     * Sauvegarde le X-Anon-Id
     */
    @Transactional
    public void saveAnonId(String anonId) {
        if (anonId != null && !anonId.isEmpty()) {
            saveCookie(ANON_ID_KEY, anonId, "vinted.fr", null);
            log.info("X-Anon-Id sauvegardé");
        }
    }

    /**
     * Récupère le X-Anon-Id
     */
    public String getAnonId() {
        return getCookieByName(ANON_ID_KEY)
                .filter(c -> c.getIsActive() && !c.isExpired())
                .map(VintedCookie::getCookieValue)
                .orElse(null);
    }
}
