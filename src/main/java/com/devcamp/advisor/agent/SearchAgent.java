package com.devcamp.advisor.agent;

import com.devcamp.advisor.model.AgentModels.SearchFindings;
import com.devcamp.advisor.model.AgentModels.UseCaseRequirements;
import com.devcamp.advisor.util.DefaultModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/**
 * AGENT 2 — Search Agent (Upgraded to Native Gemini Grounding)
 *
 * Responsibility:
 *   1. Receives requirements from the Intake Agent
 *   2. Uses Gemini's native "Grounding with Google Search" tool to find real-time model data
 *   3. Extracts a structured list of discovered AI model candidates
 *
 * NOTE: This agent no longer requires a separate Google Search API key.
 * It leverages Gemini's built-in capability to browse the live web.
 */
public class SearchAgent {

    private static final Logger log = LoggerFactory.getLogger(SearchAgent.class);

    private static final String GROUNDING_PROMPT = """
        You are the Search Agent. Your job is to search the live web for AI model candidates 
        relevant to the provided use case. 
        
        Use your GOOGLE_SEARCH tool to find:
        1. Exact model names and their providers.
        2. Key capabilities, context windows, and pricing tiers.
        3. Strengths and limitations for the specific use case.
        
        Provide a detailed textual summary of your findings.
        """;

    private static final String EXTRACTION_PROMPT = """
        You are the Search Agent. You will receive raw search findings from a web-grounded search.
        Convert these findings into a structured JSON list of model candidates.

        Rules:
        - Include between 8 and 14 models.
        - Actively include non-US models (Chinese, European, Open-Source).
        - Extract factual data: context window, pricing tier, latency profile, strengths, and limitations.

        Output ONLY valid JSON. No markdown. No explanation:
        {
          "searchSummary": "2 sentences summarising what was found on the web",
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
              "sourceHint": "brief note e.g. from official benchmark on site X"
            }
          ],
          "notableFindings": "any surprising or important findings",
          "dataFreshness": "current date or note on data age"
        }
        """;

    private final DefaultModel model;

    public SearchAgent(DefaultModel model) {
        this.model = model;
    }

    /**
     * Use Gemini Grounding in two steps to discover AI models.
     * Step 1: Grounded search (text response).
     * Step 2: Extraction/formatting (JSON response).
     */
    public SearchFindings process(UseCaseRequirements requirements) {
        log.info("◎ SEARCH AGENT — performing native Gemini grounding with Google Search...");

        String userMessage = buildUserMessage(requirements);

        // Step 1: Perform the search with grounding (Tool use + text response)
        log.info("  -> Step 1: Performing web-grounded discovery (text phase)...");
        String rawFindings = model.chat(GROUNDING_PROMPT, userMessage, true);

        // Step 2: Format the raw findings into structured JSON (No tool use + JSON response)
        log.info("  -> Step 2: Extracting structured data from search results (JSON phase)...");
        String extractionMessage = "RAW FINDINGS:\n" + rawFindings + "\n\nUSE CASE:\n" + userMessage;
        SearchFindings findings = model.chatAsJson(EXTRACTION_PROMPT, extractionMessage, SearchFindings.class);

        log.info("  ✓ Grounded discovery found {} model candidates: {}",
                findings.modelsFound != null ? findings.modelsFound.size() : 0,
                findings.modelsFound != null
                        ? findings.modelsFound.stream()
                            .map(m -> m.name).collect(Collectors.joining(", "))
                        : "none");

        return findings;
    }

    private String buildUserMessage(UseCaseRequirements req) {
        return """
               USE CASE SUMMARY: %s
               PRIMARY TASK: %s
               COST SENSITIVITY: %s
               LATENCY SENSITIVITY: %s
               SELF-HOST NEEDED: %s
               
               Please search the web for the best AI model candidates for this specific use case.
               """.formatted(
                req.useCaseSummary,
                req.primaryTask,
                req.costSensitivity,
                req.latencySensitivity,
                req.selfHostNeeded
        );
    }
}
