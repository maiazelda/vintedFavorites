package com.vintedFav.vintedFavorites;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VintedFavoritesApplication {

	public static void main(String[] args) {
		SpringApplication.run(VintedFavoritesApplication.class, args);
	}

}
