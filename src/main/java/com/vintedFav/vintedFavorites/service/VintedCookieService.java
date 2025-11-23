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
}
