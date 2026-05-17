package com.devcamp.advisor.agent;

import com.devcamp.advisor.config.AppConfig;
import com.devcamp.advisor.model.AgentModels.SearchFindings;
import com.devcamp.advisor.model.AgentModels.UseCaseRequirements;
import com.devcamp.advisor.util.DefaultModel;
import com.devcamp.advisor.util.GoogleSearchClient;
import com.devcamp.advisor.util.GoogleSearchClient.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AGENT 2 — Search Agent
 *
 * Responsibility:
 *   1. Receives search queries from the Intake Agent
 *   2. Executes them against Google Custom Search JSON API (live web)
 *   3. Feeds the raw search results to Gemini, which extracts a structured
 *      list of discovered AI model candidates
 *
 * This is the only agent that calls Google Search. All agents use Gemini for reasoning.
 *
 * Analogy:
 *   Think of this as a two-step ETL step in your Airflow DAG —
 *   first Extract (Google Search), then Transform (Gemini interprets the snippets).
 *   The result is a clean, structured list loaded for the next agent to consume.
 *
 * A2A contract:
 *   Input  → UseCaseRequirements (for context + queries)
 *   Output → SearchFindings      (passed to AnalysisAgent)
 */
public class SearchAgent {

    private static final Logger log = LoggerFactory.getLogger(SearchAgent.class);

    private static final String SYSTEM_PROMPT = """
        You are the Search Agent in a 4-agent AI Model Advisor pipeline.

        You will receive:
        1. A use case requirements summary
        2. Raw Google search results (titles + snippets + URLs)

        Your job: extract a comprehensive, unbiased list of AI model candidates
        that appear in the search results, relevant to the described use case.

        Rules:
        - Include models from ALL global providers: US, Chinese, European, open-source
        - Do NOT filter or rank — just extract factual data found in the snippets
        - If pricing or benchmark data appears in snippets, include it in strengths/limitations
        - Be honest about what was and wasn't found; note data freshness

        Output ONLY valid JSON. No markdown. No explanation:
        {
          "searchSummary": "2 sentences summarising what the searches found",
          "modelsFound": [
            {
              "name": "model name",
              "provider": "provider name",
              "country": "country of origin",
              "openSource": true or false,
              "contextWindow": "e.g. 128k tokens or unknown",
              "pricingTier": "one of: free, very_low, low, medium, high, very_high, self_hosted, unknown",
              "latencyProfile": "one of: very_fast, fast, medium, slow, unknown",
              "strengths": ["strength 1 relevant to use case", "strength 2"],
              "limitations": ["limitation 1", "limitation 2"],
              "sourceHint": "brief note e.g. appeared in benchmark comparison on X site"
            }
          ],
          "notableFindings": "any surprising or important findings",
          "dataFreshness": "note on how current the data appears to be"
        }

        Return between 6 and 14 models. Actively include non-US models if found.
        """;

    private final DefaultModel model;
    private final GoogleSearchClient googleSearch;

    public SearchAgent(DefaultModel model, GoogleSearchClient googleSearch) {
        this.model        = model;
        this.googleSearch = googleSearch;
    }

    /**
     * Execute searches against Vertex AI Search and extract discovered AI models.
     *
     * @param requirements structured requirements from IntakeAgent (contains search queries)
     * @return SearchFindings with all discovered model candidates
     */
    public SearchFindings process(UseCaseRequirements requirements) {
        log.info("◎ SEARCH AGENT — executing Vertex AI Search queries...");

        // Step 1: Execute all queries against Vertex AI Search
        List<String> queries = requirements.searchQueries;
        if (queries == null || queries.isEmpty()) {
            log.warn("  No search queries from Intake Agent — using fallback query");
            queries = List.of("best LLM AI model 2026 benchmark comparison");
        }

        // Enforce max query limit to manage API quota (100 free/day)
        if (queries.size() > AppConfig.MAX_SEARCH_QUERIES) {
            queries = queries.subList(0, AppConfig.MAX_SEARCH_QUERIES);
        }

        List<SearchResult> searchResults = googleSearch.searchMultiple(
                queries, AppConfig.SEARCH_RESULTS_PER_QUERY
        );

        log.info("  ✓ Vertex AI Search returned {} total results", searchResults.size());

        if (searchResults.isEmpty()) {
            log.warn("  No search results returned. Check ADC, Project ID, and Data Store ID.");
            return emptyFindings();
        }

        // Step 2: Feed raw snippets to Gemini for structured extraction
        // Gemini reads the text and identifies model names, capabilities, pricing
        String searchResultsText = formatSearchResults(searchResults);
        String userMessage = buildUserMessage(requirements, searchResultsText);

        log.info("  -> Sending {} search snippets to Gemini for model extraction...", searchResults.size());

        SearchFindings findings = model.chatAsJson(SYSTEM_PROMPT, userMessage, SearchFindings.class);

        log.info("  ✓ Extracted {} model candidates: {}",
                findings.modelsFound != null ? findings.modelsFound.size() : 0,
                findings.modelsFound != null
                        ? findings.modelsFound.stream()
                            .map(m -> m.name).collect(Collectors.joining(", "))
                        : "none");

        return findings;
    }

    /**
     * Format search results into a readable block for Gemini's context window.
     */
    private String formatSearchResults(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("[Result ").append(i + 1).append("]\n");
            sb.append("Title:   ").append(r.title).append("\n");
            sb.append("URL:     ").append(r.link).append("\n");
            sb.append("Snippet: ").append(r.snippet).append("\n\n");
        }
        return sb.toString();
    }

    private String buildUserMessage(UseCaseRequirements req, String searchText) {
        return """
               USE CASE SUMMARY: %s
               PRIMARY TASK: %s
               COST SENSITIVITY: %s
               LATENCY SENSITIVITY: %s
               SELF-HOST NEEDED: %s

               GOOGLE SEARCH RESULTS:
               %s
               """.formatted(
                req.useCaseSummary,
                req.primaryTask,
                req.costSensitivity,
                req.latencySensitivity,
                req.selfHostNeeded,
                searchText
        );
    }

    private SearchFindings emptyFindings() {
        SearchFindings f = new SearchFindings();
        f.searchSummary    = "No search results returned from Vertex AI Search.";
        f.modelsFound      = List.of();
        f.notableFindings  = "Search returned no results. Check Google Cloud credentials and Data Store configuration.";
        f.dataFreshness    = "N/A";
        return f;
    }
}
