package com.vintedFav.vintedFavorites.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncResponse {
    private boolean success;
    private String message;
    private Integer newItemsCount;
    private Integer totalItemsCount;
}
