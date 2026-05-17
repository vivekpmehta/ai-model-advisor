package com.devcamp.advisor.agent;

import com.devcamp.advisor.model.AgentModels.Recommendation;
import com.devcamp.advisor.model.AgentModels.ScoredAnalysis;
import com.devcamp.advisor.model.AgentModels.SearchFindings;
import com.devcamp.advisor.model.AgentModels.UseCaseRequirements;
import com.devcamp.advisor.util.DefaultModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AGENT 4 — Recommendation Agent
 *
 * Responsibility:
 *   Final synthesis. Receives all three upstream outputs and produces the
 *   definitive recommendation: primary pick, runner-up, budget option,
 *   dark horse, and an opinionated decision framework.
 *
 * Analogy:
 *   This is the presentation layer / response DTO builder in your service chain.
 *   All the business logic has been done upstream; this agent's job is to compose
 *   a clear, honest, well-structured output for the end user.
 *
 * A2A contract:
 *   Input  → UseCaseRequirements + SearchFindings + ScoredAnalysis
 *   Output → Recommendation (terminal output, rendered to console)
 */
public class RecommendationAgent {

    private static final Logger log = LoggerFactory.getLogger(RecommendationAgent.class);

    private static final String SYSTEM_PROMPT = """
        You are the Recommendation Agent — the final stage of a 4-agent AI Model Advisor pipeline.

        You receive: use case requirements, live search findings, and scored model analysis.
        Produce the definitive recommendation. Be opinionated, specific, and honest.

        Rules:
        - primary, runnerUp, and budgetPick MUST come from different providers
        - If the best fit is open-source/self-hosted, say so without hesitation
        - watchOut must be a real caveat, not a generic disclaimer
        - decisionFramework must give concrete, actionable advice for THIS use case
        - dataDisclaimer must note that results came from live Google search and may vary

        Output ONLY valid JSON. No markdown. No explanation:
        {
          "headline": "punchy 8-word-max recommendation headline",
          "primary": {
            "name": "model name",
            "provider": "provider",
            "country": "country",
            "openSource": true or false,
            "tagline": "one compelling sentence — why this wins for THIS specific use case",
            "axes": {
              "capability": 0-100,
              "speed": 0-100,
              "costEfficiency": 0-100,
              "ecosystemFit": 0-100
            },
            "idealFor": ["scenario 1", "scenario 2", "scenario 3"],
            "watchOut": "one specific, honest caveat for this use case"
          },
          "runnerUp": {
            "name": "model name",
            "provider": "different provider from primary",
            "country": "country",
            "whenToChoose": "specific condition where this beats the primary pick"
          },
          "budgetPick": {
            "name": "model name",
            "provider": "different provider from primary and runner-up",
            "country": "country",
            "openSource": true or false,
            "costNote": "concrete cost trade-off sentence"
          },
          "darkHorse": {
            "name": "model name or null",
            "provider": "provider or null",
            "whyInteresting": "unexpected or emerging option worth watching"
          },
          "decisionFramework": "3 sentences of practical advice specific to this exact use case",
          "costEstimate": "rough order-of-magnitude cost estimate given the stated volume",
          "dataDisclaimer": "one sentence noting live Google search provenance and date"
        }
        """;

    private final DefaultModel model;

    public RecommendationAgent(DefaultModel model) {
        this.model = model;
    }

    /**
     * Synthesise all upstream agent outputs into the final recommendation.
     *
     * @param requirements structured requirements (Agent 1 output)
     * @param findings     live search findings (Agent 2 output)
     * @param analysis     scored analysis (Agent 3 output)
     * @return final typed Recommendation
     */
    public Recommendation process(
            UseCaseRequirements requirements,
            SearchFindings findings,
            ScoredAnalysis analysis) {

        log.info("◆ RECOMMENDATION AGENT — synthesising final recommendation...");

        String userMessage = """
                USE CASE REQUIREMENTS:
                %s

                SEARCH FINDINGS:
                %s

                SCORED ANALYSIS:
                %s
                """.formatted(
                model.toJson(requirements),
                model.toJson(findings),
                model.toJson(analysis)
        );

        Recommendation recommendation =
                model.chatAsJson(SYSTEM_PROMPT, userMessage, Recommendation.class);

        log.info("  ✓ Headline: {}", recommendation.headline);
        log.info("  Primary:   {} by {}",
                recommendation.primary != null ? recommendation.primary.name : "?",
                recommendation.primary != null ? recommendation.primary.provider : "?");
        log.info("  Runner-up: {} by {}",
                recommendation.runnerUp != null ? recommendation.runnerUp.name : "?",
                recommendation.runnerUp != null ? recommendation.runnerUp.provider : "?");
        log.info("  Budget:    {} by {}",
                recommendation.budgetPick != null ? recommendation.budgetPick.name : "?",
                recommendation.budgetPick != null ? recommendation.budgetPick.provider : "?");

        return recommendation;
    }
}
