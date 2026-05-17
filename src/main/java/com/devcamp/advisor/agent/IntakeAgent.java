package com.devcamp.advisor.agent;

import com.devcamp.advisor.model.AgentModels.UseCaseRequirements;
import com.devcamp.advisor.util.DefaultModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AGENT 1 — Intake Agent
 *
 * Responsibility:
 *   Converts the user's freetext use case description into a structured
 *   {@link UseCaseRequirements} object, including the search queries that
 *   the Search Agent will execute against Google.
 *
 * Analogy:
 *   This is your DTO mapper + input validator at the entry point of a
 *   service chain. Raw user input comes in; clean typed contract goes out.
 *
 * A2A contract:
 *   Input  → String (raw user description)
 *   Output → UseCaseRequirements (passed to SearchAgent)
 */
public class IntakeAgent {

    private static final Logger log = LoggerFactory.getLogger(IntakeAgent.class);

    private static final String SYSTEM_PROMPT = """
        You are the Intake Agent in a 4-agent AI Model Advisor pipeline.

        Your sole job: parse the user's natural language use case description
        into a structured JSON object. Generate targeted search queries that
        the next agent will use to search Google for the best current AI models.

        CRITICAL search query rules:
        - Queries MUST be unbiased — do not favour any single vendor or country
        - Include queries that surface: open-source models, Chinese models (Qwen, DeepSeek, Baidu ERNIE),
          European models (Mistral), edge/local models, and cost-optimised models
        - Make queries specific enough to return benchmark comparisons and pricing data
        - Each query should target a different angle (capability, cost, open-source, deployment)

        Output ONLY valid JSON with this exact structure. No markdown fences. No explanation:
        {
          "useCaseSummary": "one sentence summary",
          "primaryTask": "one of: coding, writing, reasoning, data_analysis, vision, conversation, document_processing, search_rag, agents, creative, multimodal",
          "contextWindowNeeded": "one of: small_8k, medium_32k, large_128k, huge_200k_plus",
          "latencySensitivity": "one of: real_time, interactive, batch",
          "costSensitivity": "one of: cost_critical, balanced, performance_first",
          "multimodalNeeded": true or false,
          "toolUseNeeded": true or false,
          "openSourcePreferred": true or false,
          "selfHostNeeded": true or false,
          "volume": "one of: prototype, low, medium, high, very_high",
          "deploymentTarget": "one of: cloud_api, self_hosted, edge, unknown",
          "searchQueries": [
            "specific google search query 1",
            "specific google search query 2",
            "specific google search query 3",
            "specific google search query 4"
          ],
          "keyRequirements": ["up to 4 key needs as short strings"]
        }
        """;

    private final DefaultModel model;

    public IntakeAgent(DefaultModel model) {
        this.model = model;
    }

    /**
     * Parse the user's use case into structured requirements.
     *
     * @param userInput raw use case description from the user
     * @return structured requirements including Google search queries
     */
    public UseCaseRequirements process(String userInput) {
        log.info("⬡ INTAKE AGENT — parsing use case...");
        log.debug("  Input: {}", userInput);

        String userMessage = "User's use case description:\n\n" + userInput;
        UseCaseRequirements requirements =
                model.chatAsJson(SYSTEM_PROMPT, userMessage, UseCaseRequirements.class);

        log.info("  ✓ Task: {} | Latency: {} | Cost: {} | Queries: {}",
                requirements.primaryTask,
                requirements.latencySensitivity,
                requirements.costSensitivity,
                requirements.searchQueries != null ? requirements.searchQueries.size() : 0);

        return requirements;
    }
}
