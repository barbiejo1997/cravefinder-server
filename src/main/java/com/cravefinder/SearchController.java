package com.cravefinder;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SearchController {

    private final String GOOGLE_KEY = System.getenv("GOOGLE_API_KEY");
    private final RestTemplate http = new RestTemplate();

    /**
     * City coordinates map — used to bias Google results to the correct city.
     * Without this, Google sometimes returns results from other states!
     * "location" biases results toward these coordinates, and "radius" (in meters)
     * sets how far out from the city center to search.
     */
    private static final Map<String, String> CITY_COORDS = new HashMap<>();
    static {
        CITY_COORDS.put("New York,NY",      "40.7128,-74.0060");
        CITY_COORDS.put("Los Angeles,CA",   "34.0522,-118.2437");
        CITY_COORDS.put("Chicago,IL",       "41.8781,-87.6298");
        CITY_COORDS.put("Houston,TX",       "29.7604,-95.3698");
        CITY_COORDS.put("Phoenix,AZ",       "33.4484,-112.0740");
        CITY_COORDS.put("Philadelphia,PA",  "39.9526,-75.1652");
        CITY_COORDS.put("San Antonio,TX",   "29.4241,-98.4936");
        CITY_COORDS.put("San Diego,CA",     "32.7157,-117.1611");
        CITY_COORDS.put("Dallas,TX",        "32.7767,-96.7970");
        CITY_COORDS.put("Jacksonville,FL",  "30.3322,-81.6557");
        CITY_COORDS.put("Austin,TX",        "30.2672,-97.7431");
        CITY_COORDS.put("Fort Worth,TX",    "32.7555,-97.3308");
        CITY_COORDS.put("Columbus,OH",      "39.9612,-82.9988");
        CITY_COORDS.put("Charlotte,NC",     "35.2271,-80.8431");
        CITY_COORDS.put("San Francisco,CA", "37.7749,-122.4194");
        CITY_COORDS.put("Pittsburgh,PA",    "40.4406,-79.9959");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "ok",
            "service", "CraveFinder",
            "keyLoaded", GOOGLE_KEY != null ? "yes" : "NO - check env vars!"
        );
    }

    @GetMapping("/search")
    public ResponseEntity<String> search(
            @RequestParam String dish,
            @RequestParam String city) {

        if (dish == null || dish.isBlank() || city == null || city.isBlank()) {
            return ResponseEntity.badRequest()
                .body("{\"error\": \"dish and city are required\"}");
        }

        if (GOOGLE_KEY == null || GOOGLE_KEY.isBlank()) {
            return ResponseEntity.status(500)
                .body("{\"error\": \"GOOGLE_API_KEY environment variable not set\"}");
        }

        try {
            String encodedQuery = java.net.URLEncoder.encode(dish + " restaurant", "UTF-8");

            // Build URL — start with query and type
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("https://maps.googleapis.com/maps/api/place/textsearch/json");
            urlBuilder.append("?query=").append(encodedQuery);
            urlBuilder.append("&type=restaurant");

            // If we have coordinates for this city, use them to STRICTLY
            // bias results. We use two params together:
            //   location = center point (lat,lng)
            //   radius   = search radius in meters (20km covers any major city)
            // This pins results to the actual city — no more Texas results for Pittsburgh!
            String coords = CITY_COORDS.get(city);
            if (coords != null) {
                urlBuilder.append("&location=").append(coords);
                urlBuilder.append("&radius=35000");  // 35km radius
            } else {
                // City not in our map — fall back to name-based search
                urlBuilder.append("&location=").append(
                    java.net.URLEncoder.encode(city, "UTF-8")
                );
            }

            urlBuilder.append("&key=").append(GOOGLE_KEY);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> googleResponse = http.exchange(
                urlBuilder.toString(), HttpMethod.GET, request, String.class
            );

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(googleResponse.getBody());

        } catch (Exception e) {
            System.err.println("Google Places call failed: " + e.getMessage());
            return ResponseEntity.status(502)
                .body("{\"error\": \"Search failed: " + e.getMessage() + "\"}");
        }
    }
}
