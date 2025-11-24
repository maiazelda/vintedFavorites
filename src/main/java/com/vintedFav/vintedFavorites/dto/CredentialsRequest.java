package com.vintedFav.vintedFavorites.dto;

import lombok.Data;

@Data
public class CredentialsRequest {
    private String email;
    private String password;
    private String userId; // Optional: Vinted user ID for favorites API
}
