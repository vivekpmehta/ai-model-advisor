package com.devcamp.advisor.config;

/**
 * Centralised configuration loaded from environment variables.
 *
 * Required env vars:
 *   GOOGLE_API_KEY          - your Google API key for Gemini
 *   GOOGLE_PROJECT_ID       - your Google Cloud Project ID
 *   VERTEX_LOCATION         - e.g., "global" or "us"
 *   VERTEX_DATA_STORE_ID    - your Vertex AI Search Data Store ID
 *
 * NOTE: Google Custom Search JSON API is replaced by Vertex AI Search in 2026.
 * See: https://cloud.google.com/vertex-ai/docs/search/overview
 */
public class AppConfig {

    public static final String GOOGLE_API_KEY = requireEnv("GOOGLE_API_KEY");
    public static final String GOOGLE_PROJECT_ID = requireEnv("GOOGLE_PROJECT_ID");
    public static final String VERTEX_LOCATION = getEnvOrDefault("VERTEX_LOCATION", "global");
    public static final String VERTEX_DATA_STORE_ID = requireEnv("VERTEX_DATA_STORE_ID");
    public static final String VERTEX_SERVING_CONFIG_ID = getEnvOrDefault("VERTEX_SERVING_CONFIG_ID", "default_search");

    // Gemini model used by all reasoning agents
    public static final String GEMINI_MODEL = "gemini-2.5-flash";
    public static final String GEMINI_GENERATE_CONTENT_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
            GEMINI_MODEL + ":generateContent";

    // Vertex AI Search endpoint (Discovery Engine)
    public static final String VERTEX_SEARCH_URL = String.format(
            "https://discoveryengine.googleapis.com/v1/projects/%s/locations/%s/collections/default_collection/dataStores/%s/servingConfigs/%s:search",
            GOOGLE_PROJECT_ID, VERTEX_LOCATION, VERTEX_DATA_STORE_ID, VERTEX_SERVING_CONFIG_ID);

    // Results per search query (max 100 for Vertex AI Search)
    public static final int SEARCH_RESULTS_PER_QUERY = 5;

    // Max search queries the Intake Agent may generate
    public static final int MAX_SEARCH_QUERIES = 4;

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing required environment variable: " + name +
                "\nSet it before running: export " + name + "=<your-value>"
            );
        }
        return value;
    }

    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
