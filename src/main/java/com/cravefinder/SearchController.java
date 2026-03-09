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

    private final String GOOGLE_KEY    = System.getenv("GOOGLE_API_KEY");
    private final String SHEETS_URL    = System.getenv("SHEETS_SCRIPT_URL");
    private final RestTemplate http    = new RestTemplate();

    private static final Map<String, String> CITY_COORDS = new HashMap<>();
    static {
        CITY_COORDS.put("Austin,TX",        "30.2672,-97.7431");
        CITY_COORDS.put("Charlotte,NC",     "35.2271,-80.8431");
        CITY_COORDS.put("Chicago,IL",       "41.8781,-87.6298");
        CITY_COORDS.put("Columbus,OH",      "39.9612,-82.9988");
        CITY_COORDS.put("Dallas,TX",        "32.7767,-96.7970");
        CITY_COORDS.put("Denver,CO",        "39.7392,-104.9903");
        CITY_COORDS.put("El Paso,TX",       "31.7619,-106.4850");
        CITY_COORDS.put("Fort Worth,TX",    "32.7555,-97.3308");
        CITY_COORDS.put("Houston,TX",       "29.7604,-95.3698");
        CITY_COORDS.put("Indianapolis,IN",  "39.7684,-86.1581");
        CITY_COORDS.put("Jacksonville,FL",  "30.3322,-81.6557");
        CITY_COORDS.put("Las Vegas,NV",     "36.1699,-115.1398");
        CITY_COORDS.put("Los Angeles,CA",   "34.0522,-118.2437");
        CITY_COORDS.put("Louisville,KY",    "38.2527,-85.7585");
        CITY_COORDS.put("Nashville,TN",     "36.1627,-86.7816");
        CITY_COORDS.put("New York,NY",      "40.7128,-74.0060");
        CITY_COORDS.put("Oklahoma City,OK", "35.4676,-97.5164");
        CITY_COORDS.put("Philadelphia,PA",  "39.9526,-75.1652");
        CITY_COORDS.put("Phoenix,AZ",       "33.4484,-112.0740");
        CITY_COORDS.put("Pittsburgh,PA",    "40.4406,-79.9959");
        CITY_COORDS.put("San Antonio,TX",   "29.4241,-98.4936");
        CITY_COORDS.put("San Diego,CA",     "32.7157,-117.1611");
        CITY_COORDS.put("San Francisco,CA", "37.7749,-122.4194");
        CITY_COORDS.put("San Jose,CA",      "37.3382,-121.8863");
        CITY_COORDS.put("Seattle,WA",       "47.6062,-122.3321");
        CITY_COORDS.put("Washington,DC",    "38.9072,-77.0369");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status",      "ok",
            "service",     "CraveFinder",
            "googleKey",   GOOGLE_KEY  != null ? "yes" : "NO",
            "sheetsUrl",   SHEETS_URL  != null ? "yes" : "NO (community submissions disabled)"
        );
    }

    /**
     * SUBMIT ENDPOINT — saves a user community submission to Google Sheets
     * Called when a user submits a new dish/restaurant from the app
     */
    @PostMapping("/submit")
    public ResponseEntity<String> submit(@RequestBody String body) {
        if (SHEETS_URL == null || SHEETS_URL.isBlank()) {
            return ResponseEntity.status(503)
                .body("{\"error\": \"Community submissions not configured\"}");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = http.exchange(
                SHEETS_URL, HttpMethod.POST, request, String.class
            );
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(502)
                .body("{\"error\": \"Submission failed: " + e.getMessage() + "\"}");
        }
    }

    /**
     * SEARCH ENDPOINT — searches Google Places AND community submissions
     */
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
                .body("{\"error\": \"GOOGLE_API_KEY not set\"}");
        }

        try {
            // Run Google Places and community search in parallel
            String googleJson    = searchGoogle(dish, city);
            String communityJson = searchCommunity(dish, city);

            // Merge both result sets into one response
            // Simple string merge — community results go first so local tips appear at top
            String merged = mergeResults(communityJson, googleJson);

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(merged);

        } catch (Exception e) {
            System.err.println("Search failed: " + e.getMessage());
            return ResponseEntity.status(502)
                .body("{\"error\": \"Search failed: " + e.getMessage() + "\"}");
        }
    }

    private String searchGoogle(String dish, String city) throws Exception {
        String encodedQuery = java.net.URLEncoder.encode(dish + " restaurant", "UTF-8");
        StringBuilder url = new StringBuilder();
        url.append("https://maps.googleapis.com/maps/api/place/textsearch/json");
        url.append("?query=").append(encodedQuery);
        url.append("&type=restaurant");

        String coords = CITY_COORDS.get(city);
        if (coords != null) {
            url.append("&location=").append(coords);
            url.append("&radius=35000");
        }
        url.append("&key=").append(GOOGLE_KEY);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        ResponseEntity<String> response = http.exchange(
            url.toString(), HttpMethod.GET, new HttpEntity<>(headers), String.class
        );
        return response.getBody();
    }

    private String searchCommunity(String dish, String city) {
        if (SHEETS_URL == null || SHEETS_URL.isBlank()) return "{\"results\":[]}";
        try {
            String url = SHEETS_URL
                + "?dish=" + java.net.URLEncoder.encode(dish, "UTF-8")
                + "&city=" + java.net.URLEncoder.encode(city, "UTF-8");
            ResponseEntity<String> response = http.exchange(
                url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class
            );
            return response.getBody();
        } catch (Exception e) {
            System.err.println("Community search failed (non-fatal): " + e.getMessage());
            return "{\"results\":[]}";
        }
    }

    /**
     * Merges community results (from Sheets) with Google results.
     * Community results appear first so local tips get visibility.
     * This is a simple string approach — extracts the arrays and combines them.
     */
    private String mergeResults(String communityJson, String googleJson) {
        try {
            // Extract the results arrays from each JSON string
            String communityArray = extractResultsArray(communityJson);
            String googleArray    = extractResultsArray(googleJson);

            // Combine: community first, then Google
            String combined;
            if (communityArray.equals("[]")) {
                combined = googleArray;
            } else if (googleArray.equals("[]")) {
                combined = communityArray;
            } else {
                // Remove the closing ] from community and opening [ from google, join with comma
                combined = communityArray.substring(0, communityArray.length() - 1)
                         + ","
                         + googleArray.substring(1);
            }

            return "{\"results\":" + combined + ",\"status\":\"OK\"}";
        } catch (Exception e) {
            // If merge fails, just return Google results
            return googleJson;
        }
    }

    private String extractResultsArray(String json) {
        if (json == null) return "[]";
        int start = json.indexOf("[");
        int end   = json.lastIndexOf("]");
        if (start == -1 || end == -1) return "[]";
        return json.substring(start, end + 1);
    }
}
