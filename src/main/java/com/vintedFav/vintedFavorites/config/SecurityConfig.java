package com.vintedFav.vintedFavorites.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuration Spring Security.
 *
 * - L'interface web (frontend) est protégée par login/password
 * - L'endpoint /api/extension/** est protégé par une clé API
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.username:admin}")
    private String username;

    @Value("${app.security.password:admin}")
    private String password;

    @Value("${app.security.api-key:changeme}")
    private String apiKey;

    /**
     * Encodeur de mot de passe.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Utilisateur en mémoire pour l'authentification.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        var user = User.builder()
                .username(username)
                .password(passwordEncoder().encode(password))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    /**
     * Configuration de sécurité pour l'API extension (clé API).
     * Cette config est prioritaire (Order 1).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain extensionSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/extension/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new ApiKeyAuthFilter(apiKey), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            );
        return http.build();
    }

    /**
     * Configuration de sécurité pour le reste de l'app (HTTP Basic).
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Permettre l'accès aux ressources statiques
                .requestMatchers("/", "/index.html", "/static/**", "/*.js", "/*.css", "/*.ico", "/*.png").permitAll()
                // Tout le reste nécessite une authentification
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .formLogin(Customizer.withDefaults());
        return http.build();
    }
}
