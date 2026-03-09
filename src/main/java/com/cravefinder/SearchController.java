package com.cravefinder;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

/**
 * ============================================================
 * UPDATED: Now uses Google Places API (Text Search)
 * Google has the best restaurant data of any free-tier API.
 * Their Text Search lets us search by dish + city — perfect
 * for CraveFinder.
 * ============================================================
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")  // Fixes the CORS problem — allows browser calls
public class SearchController {

    // Read Google API key from Render's environment variables
    // NEVER hardcode a real key in source code!
    private final String GOOGLE_KEY = System.getenv("GOOGLE_API_KEY");

    private final RestTemplate http = new RestTemplate();

    /**
     * HEALTH CHECK — visit /api/health to verify server is alive
     * and the API key is loaded correctly
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "ok",
            "service", "CraveFinder",
            "keyLoaded", GOOGLE_KEY != null ? "yes" : "NO - check env vars!"
        );
    }

    /**
     * MAIN SEARCH ENDPOINT
     * URL: GET /api/search?dish=chicken+caesar+wrap&city=Pittsburgh,PA
     *
     * Calls Google Places Text Search API, which searches for
     * restaurants matching the dish query in the given city.
     */
    @GetMapping("/search")
    public ResponseEntity<String> search(
            @RequestParam String dish,
            @RequestParam String city) {

        // Always validate inputs first
        if (dish == null || dish.isBlank() || city == null || city.isBlank()) {
            return ResponseEntity.badRequest()
                .body("{\"error\": \"dish and city parameters are required\"}");
        }

        if (GOOGLE_KEY == null || GOOGLE_KEY.isBlank()) {
            return ResponseEntity.status(500)
                .body("{\"error\": \"GOOGLE_API_KEY environment variable not set\"}");
        }

        try {
            // Build search query: "chicken caesar wrap restaurant Pittsburgh PA"
            // Google Text Search understands natural language queries like this
            String query = dish + " restaurant " + city;
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");

            String url = "https://maps.googleapis.com/maps/api/place/textsearch/json"
                       + "?query=" + encodedQuery
                       + "&type=restaurant"
                       + "&key=" + GOOGLE_KEY;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> googleResponse = http.exchange(
                url, HttpMethod.GET, request, String.class
            );

            // Pass Google's response back to the browser
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
