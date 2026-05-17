package com.devcamp.advisor.agent;

import com.devcamp.advisor.model.AgentModels.ScoredAnalysis;
import com.devcamp.advisor.model.AgentModels.SearchFindings;
import com.devcamp.advisor.model.AgentModels.UseCaseRequirements;
import com.devcamp.advisor.util.DefaultModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AGENT 3 — Analysis Agent
 *
 * Responsibility:
 *   Scores every model discovered by the Search Agent across four axes:
 *   capability fit, speed fit, cost fit, and ecosystem fit — all relative
 *   to the specific use case requirements.
 *
 * This agent never calls external APIs. It is pure reasoning over the
 * structured data passed from the two upstream agents.
 *
 * Analogy:
 *   This is your business logic / scoring service. It receives normalised
 *   data from two upstream services (Intake + Search) and applies domain
 *   rules to produce a scored output. No I/O — just CPU.
 *
 * A2A contract:
 *   Input  → UseCaseRequirements + SearchFindings
 *   Output → ScoredAnalysis (passed to RecommendationAgent)
 */
public class AnalysisAgent {

    private static final Logger log = LoggerFactory.getLogger(AnalysisAgent.class);

    private static final String SYSTEM_PROMPT = """
        You are the Analysis Agent in a 4-agent AI Model Advisor pipeline.

        You receive:
        1. Structured use case requirements from the Intake Agent
        2. A list of AI model candidates discovered by the Search Agent from live Google results

        Your job: score each model fairly and objectively against the specific requirements.

        Scoring methodology:
        - capability_fit (0-100): how well the model's known strengths match the primary task
        - speed_fit (0-100):      how well latency profile matches the latency sensitivity requirement
        - cost_fit (0-100):       how well pricing tier matches cost sensitivity
        - ecosystem_fit (0-100):  suitability for the deployment target and tech stack signals

        Rules:
        - Penalise models that fail hard requirements (e.g. can't self-host when required)
        - Do NOT favour any provider's models systematically
        - The top_3_names list MUST span at least 2 different providers
        - If open-source is preferred, weight open-source models higher in cost_fit
        - composite_score = weighted average: capability(40%) + speed(20%) + cost(25%) + ecosystem(15%)

        Output ONLY valid JSON. No markdown. No explanation:
        {
          "scoringRationale": "2-3 sentences on your methodology for this specific use case",
          "scoredModels": [
            {
              "name": "model name",
              "provider": "provider",
              "country": "country",
              "openSource": true or false,
              "capabilityFit": 0-100,
              "speedFit": 0-100,
              "costFit": 0-100,
              "ecosystemFit": 0-100,
              "compositeScore": 0-100,
              "whyFits": "one sentence specific to this use case",
              "keyTradeOff": "one honest trade-off for this use case"
            }
          ],
          "diversityCheck": "note confirming results span multiple providers and geographies",
          "top3Names": ["name1", "name2", "name3"]
        }

        Sort scoredModels by compositeScore descending.
        top3Names MUST come from at least 2 different providers.
        """;

    private final DefaultModel model;

    public AnalysisAgent(DefaultModel model) {
        this.model = model;
    }

    /**
     * Score all discovered models against the use case requirements.
     *
     * @param requirements structured requirements from IntakeAgent
     * @param findings     model candidates discovered by SearchAgent
     * @return scored and ranked analysis
     */
    public ScoredAnalysis process(UseCaseRequirements requirements, SearchFindings findings) {
        log.info("◈ ANALYSIS AGENT — scoring {} models...",
                findings.modelsFound != null ? findings.modelsFound.size() : 0);

        String userMessage = """
                USE CASE REQUIREMENTS:
                %s

                DISCOVERED MODELS FROM LIVE SEARCH:
                %s
                """.formatted(
                model.toJson(requirements),
                model.toJson(findings)
        );

        ScoredAnalysis analysis = model.chatAsJson(SYSTEM_PROMPT, userMessage, ScoredAnalysis.class);

        log.info("  ✓ Scored {} models | Top 3: {}",
                analysis.scoredModels != null ? analysis.scoredModels.size() : 0,
                analysis.top3Names);
        log.info("  Diversity: {}", analysis.diversityCheck);

        return analysis;
    }
}
