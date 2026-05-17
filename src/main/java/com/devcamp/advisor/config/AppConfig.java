package com.devcamp.advisor.config;

/**
 * Centralised configuration loaded from environment variables.
 *
 * Required env vars:
 *   GOOGLE_API_KEY          - your Google API key for Gemini and Custom Search
 *   GOOGLE_SEARCH_ENGINE_ID - your Programmable Search Engine (cx) ID
 *
 * NOTE: Google Custom Search JSON API is closed to new customers as of 2026.
 * Existing customers continue to work; new sign-ups should use Vertex AI Search.
 * See: https://developers.google.com/custom-search/v1/overview
 */
public class AppConfig {

    public static final String GOOGLE_API_KEY = requireEnv("GOOGLE_API_KEY");
    public static final String GOOGLE_SEARCH_ENGINE_ID = requireEnv("GOOGLE_SEARCH_ENGINE_ID");

    // Gemini model used by all reasoning agents
    public static final String GEMINI_MODEL = "gemini-2.5-flash";
    public static final String GEMINI_GENERATE_CONTENT_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
            GEMINI_MODEL + ":generateContent";

    // Google Custom Search endpoint
    public static final String GOOGLE_SEARCH_URL =
            "https://www.googleapis.com/customsearch/v1";

    // Results per search query (max 10 per Google's API limit)
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
}
