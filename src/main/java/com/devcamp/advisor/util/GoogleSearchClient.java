package com.devcamp.advisor.util;

import com.devcamp.advisor.config.AppConfig;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for Vertex AI Search (Discovery Engine) REST API.
 *
 * Endpoint: POST https://discoveryengine.googleapis.com/v1/...:search
 * Docs:     https://cloud.google.com/vertex-ai/docs/search/overview
 *
 * NOTE: This client has been upgraded from Google Custom Search (phased out 2026)
 * to Vertex AI Search. It uses Google Application Default Credentials (ADC) for
 * OAuth2 authentication, while Gemini calls continue to use the API Key.
 *
 * Required env vars for this client:
 *   GOOGLE_PROJECT_ID
 *   VERTEX_DATA_STORE_ID
 *   GOOGLE_APPLICATION_CREDENTIALS (path to service account JSON)
 */
public class GoogleSearchClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleSearchClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;
    private GoogleCredentials credentials;

    public GoogleSearchClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        
        try {
            // Load application default credentials (ADC) for Vertex AI Search
            // Requires GOOGLE_APPLICATION_CREDENTIALS env var or running on GCP
            this.credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
            log.info("Google Application Default Credentials loaded successfully.");
        } catch (IOException e) {
            log.error("Failed to load Google Application Default Credentials: {}", e.getMessage());
            log.warn("Vertex AI Search calls will fail. Ensure GOOGLE_APPLICATION_CREDENTIALS is set.");
        }
    }

    /**
     * Represents a single search result item.
     */
    public static class SearchResult {
        public final String title;
        public final String link;
        public final String snippet;

        public SearchResult(String title, String link, String snippet) {
            this.title   = title;
            this.link    = link;
            this.snippet = snippet;
        }

        @Override
        public String toString() {
            return "[" + title + "] " + snippet + " (" + link + ")";
        }
    }

    /**
     * Execute a search query against Vertex AI Search.
     *
     * @param query      the search query string
     * @param numResults number of results to return
     * @return list of SearchResult; empty list on error
     */
    public List<SearchResult> search(String query, int numResults) {
        log.info("  ◎ Vertex AI Search: \"{}\" (requesting {} results)", query, numResults);

        String token = getAccessToken();
        if (token == null) {
            log.error("No access token available. Check service account configuration.");
            return Collections.emptyList();
        }

        // Vertex AI Search payload structure (Website Search)
        JsonObject payload = new JsonObject();
        payload.addProperty("query", query);
        payload.addProperty("pageSize", Math.min(numResults, 100));
        payload.addProperty("userPseudoId", "anonymous-advisor-user");

        RequestBody body = RequestBody.create(gson.toJson(payload), JSON);

        Request request = new Request.Builder()
                .url(AppConfig.VERTEX_SEARCH_URL)
                .header("Authorization", "Bearer " + token)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Vertex AI Search API error: HTTP {} for query \"{}\"", response.code(), query);
                String errBody = response.body() != null ? response.body().string() : "empty";
                log.warn("Response details: {}", errBody);
                return Collections.emptyList();
            }

            String responseBody = response.body().string();
            return parseResults(responseBody);

        } catch (IOException e) {
            log.error("Network error calling Vertex AI Search API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Execute multiple queries and aggregate results.
     */
    public List<SearchResult> searchMultiple(List<String> queries, int perQuery) {
        List<SearchResult> all = new ArrayList<>();
        java.util.Set<String> seenLinks = new java.util.HashSet<>();

        for (String query : queries) {
            List<SearchResult> results = search(query, perQuery);
            for (SearchResult r : results) {
                if (seenLinks.add(r.link)) {
                    all.add(r);
                }
            }
        }

        log.info("  ◎ Search total: {} unique results across {} queries",
                all.size(), queries.size());
        return all;
    }

    /**
     * Parse the Vertex AI Search JSON response.
     */
    private List<SearchResult> parseResults(String json) {
        List<SearchResult> results = new ArrayList<>();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            JsonArray resultsArr = root.getAsJsonArray("results");
            if (resultsArr == null) return results;

            for (JsonElement element : resultsArr) {
                JsonObject resultObj = element.getAsJsonObject();
                JsonObject document = resultObj.getAsJsonObject("document");
                if (document == null) continue;

                JsonObject derivedStructData = document.getAsJsonObject("derivedStructData");
                if (derivedStructData == null) continue;

                String title = getStringSafe(derivedStructData, "title");
                String link = getStringSafe(derivedStructData, "link");
                
                // Extract snippet from snippets array (preferred for website search)
                String snippet = "";
                JsonArray snippets = derivedStructData.getAsJsonArray("snippets");
                if (snippets != null && snippets.size() > 0) {
                    JsonObject firstSnippet = snippets.get(0).getAsJsonObject();
                    snippet = getStringSafe(firstSnippet, "snippet");
                } else {
                    snippet = getStringSafe(derivedStructData, "snippet"); // Fallback
                }

                results.add(new SearchResult(title, link, snippet));
            }
        } catch (Exception e) {
            log.error("Failed to parse Vertex AI Search response: {}", e.getMessage());
        }
        return results;
    }

    private String getAccessToken() {
        if (credentials == null) return null;
        try {
            credentials.refreshIfExpired();
            AccessToken token = credentials.getAccessToken();
            return token.getTokenValue();
        } catch (IOException e) {
            log.error("Failed to refresh Google access token: {}", e.getMessage());
            return null;
        }
    }

    private String getStringSafe(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : "";
    }
}
