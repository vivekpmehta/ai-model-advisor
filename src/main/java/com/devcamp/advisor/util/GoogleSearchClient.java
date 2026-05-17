package com.devcamp.advisor.util;

import com.devcamp.advisor.config.AppConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for Google Custom Search JSON API.
 *
 * Endpoint: GET https://www.googleapis.com/customsearch/v1
 * Docs:     https://developers.google.com/custom-search/v1/reference/rest/v1/cse/list
 *
 * NOTE: Google Custom Search API is closed to new sign-ups as of 2026.
 * Existing keys continue to work through January 2027.
 * New projects should evaluate Vertex AI Search as an alternative.
 *
 * Analogy: this is your WebClient for one external REST API —
 * stateless, reusable, returns typed SearchResult objects.
 */
public class GoogleSearchClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleSearchClient.class);

    private final OkHttpClient httpClient;
    private final Gson gson;

    public GoogleSearchClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
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
     * Execute a single search query and return up to {@code numResults} results.
     *
     * @param query      the search query string
     * @param numResults number of results to return (1–10, Google's hard limit per request)
     * @return list of SearchResult; empty list on error or no results
     */
    public List<SearchResult> search(String query, int numResults) {
        log.info("  ◎ Google Search: \"{}\" (requesting {} results)", query, numResults);

        HttpUrl url = HttpUrl.parse(AppConfig.GOOGLE_SEARCH_URL).newBuilder()
                .addQueryParameter("key", AppConfig.GOOGLE_API_KEY)
                .addQueryParameter("cx",  AppConfig.GOOGLE_SEARCH_ENGINE_ID)
                .addQueryParameter("q",   query)
                .addQueryParameter("num", String.valueOf(Math.min(numResults, 10)))
                .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Google Search API error: HTTP {} for query \"{}\"", response.code(), query);
                log.warn("Response body: {}", response.body() != null ? response.body().string() : "null");
                return Collections.emptyList();
            }

            String body = response.body().string();
            return parseResults(body);

        } catch (IOException e) {
            log.error("Network error calling Google Search API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Execute multiple queries and aggregate results.
     * Deduplicates by URL.
     *
     * @param queries    list of search query strings
     * @param perQuery   results to fetch per query
     * @return aggregated, deduplicated list
     */
    public List<SearchResult> searchMultiple(List<String> queries, int perQuery) {
        List<SearchResult> all = new ArrayList<>();
        java.util.Set<String> seenLinks = new java.util.HashSet<>();

        for (String query : queries) {
            List<SearchResult> results = search(query, perQuery);
            for (SearchResult r : results) {
                if (seenLinks.add(r.link)) {   // Set.add returns false for duplicates
                    all.add(r);
                }
            }
        }

        log.info("  ◎ Google Search total: {} unique results across {} queries",
                all.size(), queries.size());
        return all;
    }

    /**
     * Parse the Google Custom Search JSON response into SearchResult objects.
     *
     * Google response shape:
     * {
     *   "items": [
     *     { "title": "...", "link": "...", "snippet": "..." },
     *     ...
     *   ]
     * }
     */
    private List<SearchResult> parseResults(String json) {
        List<SearchResult> results = new ArrayList<>();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            JsonArray items = root.getAsJsonArray("items");
            if (items == null) return results;

            for (JsonElement element : items) {
                JsonObject item = element.getAsJsonObject();
                String title   = getStringSafe(item, "title");
                String link    = getStringSafe(item, "link");
                String snippet = getStringSafe(item, "snippet");
                results.add(new SearchResult(title, link, snippet));
            }
        } catch (Exception e) {
            log.error("Failed to parse Google Search response: {}", e.getMessage());
        }
        return results;
    }

    private String getStringSafe(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : "";
    }
}
