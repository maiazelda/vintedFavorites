package com.vintedFav.vintedFavorites;

import com.vintedFav.vintedFavorites.model.Favorite;
import com.vintedFav.vintedFavorites.repository.FavoriteRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class DataLoader {

    @Bean
    CommandLineRunner loadData(FavoriteRepository repository) {
        return args -> {
            // Vérifie si des données existent déjà
            if (repository.count() == 0) {
                // Crée des données de test
                repository.save(new Favorite(null, "vinted-001", "Robe Zara rouge", "Zara", "Robe", "Femme", 25.99,
                        "https://example.com/img1.jpg", "https://vinted.fr/items/1", LocalDateTime.now().minusDays(5),
                        false, "Sophie", "M", "Très bon état", null, null));

                repository.save(new Favorite(null, "vinted-002", "Jean Levis 501", "Levis", "Pantalon", "Homme", 45.00,
                        "https://example.com/img2.jpg", "https://vinted.fr/items/2", LocalDateTime.now().minusDays(3),
                        false, "Pierre", "32", "Neuf", null, null));

                repository.save(new Favorite(null, "vinted-003", "Basket Nike Air", "Nike", "Chaussures", "Homme", 60.00,
                        "https://example.com/img3.jpg", "https://vinted.fr/items/3", LocalDateTime.now().minusDays(1),
                        true, "Marie", "42", "Bon état", null, null));

                System.out.println("✅ Données de test chargées !");
            }
        };
    }
}