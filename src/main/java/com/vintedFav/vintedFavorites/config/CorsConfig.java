package com.vintedFav.vintedFavorites.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:8080}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Config pour le frontend (avec credentials)
        CorsConfiguration frontendConfig = new CorsConfiguration();
        frontendConfig.setAllowCredentials(true);
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        frontendConfig.setAllowedOrigins(origins);
        frontendConfig.addAllowedHeader("*");
        frontendConfig.addAllowedMethod("*");
        frontendConfig.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Total-Count"
        ));

        // Config pour l'extension Chrome (sans credentials, toutes origines)
        // Les extensions ont une origine "chrome-extension://[id]"
        CorsConfiguration extensionConfig = new CorsConfiguration();
        extensionConfig.setAllowCredentials(false);
        extensionConfig.addAllowedOriginPattern("*"); // Permet toutes les origines
        extensionConfig.addAllowedHeader("*");
        extensionConfig.addAllowedMethod("*");

        // Appliquer les configs aux paths
        source.registerCorsConfiguration("/api/extension/**", extensionConfig);
        source.registerCorsConfiguration("/**", frontendConfig);

        return new CorsFilter(source);
    }
}
