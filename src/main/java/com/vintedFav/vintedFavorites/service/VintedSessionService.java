package com.vintedFav.vintedFavorites.service;

import com.vintedFav.vintedFavorites.model.VintedCredentials;
import com.vintedFav.vintedFavorites.repository.VintedCredentialsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class VintedSessionService {

    private final VintedCredentialsRepository credentialsRepository;

    @Value("${vinted.scripts.path:scripts}")
    private String scriptsPath;

    @Value("${vinted.session.auto-refresh:true}")
    private boolean autoRefreshEnabled;

    // Simple encryption key - in production, use a proper secret management
    private static final String ENCRYPTION_KEY = "VintedFav2024SecretKey";

    // Flag to prevent multiple concurrent refresh attempts
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    /**
     * Save Vinted credentials (password is encoded, not plaintext stored)
     */
    @Transactional
    public VintedCredentials saveCredentials(String email, String password, String userId) {
        // Deactivate any existing credentials
        credentialsRepository.deactivateAll();

        VintedCredentials credentials = new VintedCredentials();
        credentials.setEmail(email);
        credentials.setPasswordEncrypted(encodePassword(password));
        credentials.setUserId(userId);
        credentials.setIsActive(true);

        log.info("Saving Vinted credentials for email: {}", email);
        return credentialsRepository.save(credentials);
    }

    /**
     * Get active credentials
     */
    public Optional<VintedCredentials> getActiveCredentials() {
        return credentialsRepository.findByIsActiveTrue();
    }

    /**
     * Check if credentials are configured
     */
    public boolean hasCredentials() {
        return getActiveCredentials().isPresent();
    }

    /**
     * Trigger a session refresh using Playwright
     * Returns a CompletableFuture that completes when refresh is done
     */
    public CompletableFuture<Boolean> refreshSession() {
        if (!autoRefreshEnabled) {
            log.warn("Auto-refresh is disabled");
            return CompletableFuture.completedFuture(false);
        }

        if (!refreshInProgress.compareAndSet(false, true)) {
            log.info("Session refresh already in progress, skipping...");
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<VintedCredentials> credentialsOpt = getActiveCredentials();
                if (credentialsOpt.isEmpty()) {
                    log.error("No credentials configured for session refresh");
                    return false;
                }

                VintedCredentials credentials = credentialsOpt.get();
                String email = credentials.getEmail();
                String password = decodePassword(credentials.getPasswordEncrypted());

                log.info("Starting automated session refresh for {}", email);

                // Build the command to run the Playwright script
                ProcessBuilder pb = new ProcessBuilder(
                        "node",
                        scriptsPath + "/vinted-session-manager.js",
                        "--email", email,
                        "--password", password
                );

                pb.redirectErrorStream(true);
                Process process = pb.start();

                // Read output
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        log.debug("Playwright: {}", line);
                    }
                }

                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    log.info("Session refresh completed successfully");
                    credentials.setLastRefresh(LocalDateTime.now());
                    credentialsRepository.save(credentials);
                    return true;
                } else {
                    log.error("Session refresh failed with exit code: {}", exitCode);
                    log.error("Output: {}", output.toString());
                    return false;
                }

            } catch (Exception e) {
                log.error("Error during session refresh: {}", e.getMessage(), e);
                return false;
            } finally {
                refreshInProgress.set(false);
            }
        });
    }

    /**
     * Check if a refresh is currently in progress
     */
    public boolean isRefreshInProgress() {
        return refreshInProgress.get();
    }

    /**
     * Simple Base64 encoding for password storage
     * Note: In production, use proper encryption (AES, etc.)
     */
    private String encodePassword(String password) {
        return Base64.getEncoder().encodeToString(password.getBytes());
    }

    /**
     * Decode the stored password
     */
    private String decodePassword(String encoded) {
        return new String(Base64.getDecoder().decode(encoded));
    }

    /**
     * Delete all credentials
     */
    @Transactional
    public void deleteAllCredentials() {
        credentialsRepository.deleteAll();
        log.info("All credentials deleted");
    }
}
