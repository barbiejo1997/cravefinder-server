package com.cravefinder;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

/**
 * ============================================================
 * WHAT IS THIS FILE?
 * ============================================================
 * This is the CONTROLLER — the most important file.
 * It defines what your server does when your webpage calls it.
 *
 * Think of it like a restaurant:
 *   - Your webpage = a customer placing an order
 *   - This controller = the waiter taking the order
 *   - Foursquare API = the kitchen making the food
 *   - The response = the food being delivered back
 *
 * KEY JAVA CONCEPTS IN THIS FILE:
 *   @RestController  — marks this class as a web handler
 *   @GetMapping      — maps a URL path to a method
 *   @RequestParam    — reads query parameters from the URL
 *   @CrossOrigin     — allows your webpage to call this server
 *                      (this is what fixes the CORS problem!)
 * ============================================================
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")   // ← THIS is the CORS fix!
                               // It tells the server: "allow requests
                               // from any webpage" — safe for our use case
public class SearchController {

    /**
     * The Foursquare API key is read from an ENVIRONMENT VARIABLE.
     *
     * An environment variable is like a secret note stored on the
     * server — your code can read it, but it never appears in your
     * source code. This keeps your API key safe.
     *
     * On Render.com you'll set this in the dashboard (we'll show you how).
     */
    private final String FSQ_KEY = System.getenv("FOURSQUARE_API_KEY");

    /**
     * RestTemplate is Spring's built-in HTTP client.
     * It lets Java make web requests — just like fetch() in JavaScript.
     */
    private final RestTemplate http = new RestTemplate();

    /**
     * HEALTH CHECK ENDPOINT
     * URL: GET /api/health
     *
     * Render.com pings this URL to check your server is alive.
     * Also useful for you to verify deployment worked.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "ok",
            "service", "CraveFinder",
            "keyLoaded", FSQ_KEY != null ? "yes" : "NO - check env vars!"
        );
    }

    /**
     * MAIN SEARCH ENDPOINT
     * URL: GET /api/search?dish=chicken+caesar+wrap&city=Pittsburgh,PA
     *
     * Your webpage calls this URL. This method:
     *   1. Receives the dish and city from the URL parameters
     *   2. Builds a Foursquare API request
     *   3. Calls Foursquare (server→server, no CORS issue!)
     *   4. Returns the results as JSON back to your webpage
     *
     * @param dish  - what food the user searched for
     * @param city  - which city to search in
     * @return      - JSON string of restaurant results
     */
    @GetMapping("/search")
    public ResponseEntity<String> search(
            @RequestParam String dish,
            @RequestParam String city) {

        // Step 1: Validate inputs — always check user input!
        if (dish == null || dish.isBlank() || city == null || city.isBlank()) {
            return ResponseEntity
                .badRequest()
                .body("{\"error\": \"dish and city parameters are required\"}");
        }

        if (FSQ_KEY == null || FSQ_KEY.isBlank()) {
            return ResponseEntity
                .status(500)
                .body("{\"error\": \"FOURSQUARE_API_KEY environment variable not set on server\"}");
        }

        // Step 2: Build the Foursquare URL
        // We ask for: name, categories, location, rating, website, phone number
        String fields = "name,categories,location,rating,website,tel,link";
        String fsqUrl = String.format(
            "https://api.foursquare.com/v3/places/search?query=%s&near=%s&limit=20&fields=%s",
            dish.replace(" ", "+"),
            city.replace(" ", "+"),
            fields
        );

        // Step 3: Set up the Authorization header with our API key
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", FSQ_KEY);   // Foursquare uses key directly (no "Bearer" prefix)
        headers.set("Accept", "application/json");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Step 4: Make the actual call to Foursquare
        // If something goes wrong, we catch the error and return a helpful message
        try {
            ResponseEntity<String> fsqResponse = http.exchange(
                fsqUrl,
                HttpMethod.GET,
                request,
                String.class
            );

            // Step 5: Pass Foursquare's response straight back to the browser
            return ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fsqResponse.getBody());

        } catch (Exception e) {
            // Something went wrong — log it and tell the browser
            System.err.println("Foursquare call failed: " + e.getMessage());
            return ResponseEntity
                .status(502)
                .body("{\"error\": \"Failed to reach Foursquare: " + e.getMessage() + "\"}");
        }
    }
}
