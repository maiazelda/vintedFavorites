package com.vintedFav.vintedFavorites.dto;

import lombok.Data;
import java.util.Map;

@Data
public class CookieUpdateRequest {
    private Map<String, String> cookies;
    private String rawCookies; // Alternative: cookies en format string "name1=value1; name2=value2"
    private String csrfToken;  // X-Csrf-Token header value
    private String anonId;     // X-Anon-Id header value
}
