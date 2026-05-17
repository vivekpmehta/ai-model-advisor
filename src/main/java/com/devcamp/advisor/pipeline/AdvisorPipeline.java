package com.devcamp.advisor.pipeline;

import com.devcamp.advisor.agent.AnalysisAgent;
import com.devcamp.advisor.agent.IntakeAgent;
import com.devcamp.advisor.agent.RecommendationAgent;
import com.devcamp.advisor.agent.SearchAgent;
import com.devcamp.advisor.model.AgentModels.*;
import com.devcamp.advisor.util.DefaultModel;
import com.devcamp.advisor.util.GoogleSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * AdvisorPipeline — top-level orchestrator.
 *
 * Wires all four agents together and drives the A2A handoff chain:
 *
 *   IntakeAgent  →  SearchAgent  →  AnalysisAgent  →  RecommendationAgent
 *       [1]             [2]               [3]                 [4]
 *   Parse input    Google Search      Score models        Final output
 *
 * Analogy:
 *   This is your Airflow DAG definition file — it doesn't do the work,
 *   it just wires the tasks and passes data between them in order.
 *
 * Run with:
 *   export GOOGLE_API_KEY=AIza...
 *   export GOOGLE_SEARCH_ENGINE_ID=abc123...
 *   ./gradlew run
 */
public class AdvisorPipeline {

    private static final Logger log = LoggerFactory.getLogger(AdvisorPipeline.class);

    // ── Shared infrastructure ────────────────────────────────────────────────
    private final DefaultModel       defaultModel;
    private final GoogleSearchClient googleSearchClient;

    // ── Agents ───────────────────────────────────────────────────────────────
    private final IntakeAgent          intakeAgent;
    private final SearchAgent          searchAgent;
    private final AnalysisAgent        analysisAgent;
    private final RecommendationAgent  recommendationAgent;

    public AdvisorPipeline() {
        // Shared clients — constructed once, injected into agents (like Spring beans)
        this.defaultModel       = new DefaultModel();
        this.googleSearchClient = new GoogleSearchClient();

        // Agent instantiation with dependency injection
        this.intakeAgent         = new IntakeAgent(defaultModel);
        this.searchAgent         = new SearchAgent(defaultModel, googleSearchClient);
        this.analysisAgent       = new AnalysisAgent(defaultModel);
        this.recommendationAgent = new RecommendationAgent(defaultModel);
    }

    /**
     * Run the full 4-agent pipeline for a given use case.
     *
     * @param useCase freetext description from the user
     * @return final Recommendation
     */
    public Recommendation run(String useCase) {
        System.out.println(banner());
        System.out.println("USE CASE: " + useCase);
        System.out.println("─".repeat(70));

        long start = System.currentTimeMillis();

        // ── AGENT 1: Intake ──────────────────────────────────────────────────
        System.out.println("\n[1/4] INTAKE AGENT — parsing requirements...");
        UseCaseRequirements requirements = intakeAgent.process(useCase);
        System.out.println("  Task:     " + requirements.primaryTask);
        System.out.println("  Latency:  " + requirements.latencySensitivity);
        System.out.println("  Cost:     " + requirements.costSensitivity);
        System.out.println("  Queries:  " + requirements.searchQueries);

        // ── AGENT 2: Search ──────────────────────────────────────────────────
        System.out.println("\n[2/4] SEARCH AGENT — querying Google Custom Search API...");
        SearchFindings findings = searchAgent.process(requirements);
        System.out.println("  Models found: " +
                (findings.modelsFound != null ? findings.modelsFound.size() : 0));
        if (findings.modelsFound != null) {
            findings.modelsFound.forEach(m ->
                    System.out.println("    • " + m.name + " (" + m.provider + ", " + m.country + ")" +
                                       (m.openSource ? " [open-source]" : "")));
        }

        // ── AGENT 3: Analysis ────────────────────────────────────────────────
        System.out.println("\n[3/4] ANALYSIS AGENT — scoring models against requirements...");
        ScoredAnalysis analysis = analysisAgent.process(requirements, findings);
        System.out.println("  Top 3: " + analysis.top3Names);
        System.out.println("  " + analysis.diversityCheck);

        // ── AGENT 4: Recommendation ──────────────────────────────────────────
        System.out.println("\n[4/4] RECOMMENDATION AGENT — synthesising final recommendation...");
        Recommendation recommendation = recommendationAgent.process(requirements, findings, analysis);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("\n" + "═".repeat(70));
        renderRecommendation(recommendation);
        System.out.printf("\n  Pipeline completed in %.1fs%n", elapsed / 1000.0);
        System.out.println("═".repeat(70));

        return recommendation;
    }

    // ── Console rendering ────────────────────────────────────────────────────

    private void renderRecommendation(Recommendation r) {
        System.out.println("  RECOMMENDATION: " + r.headline.toUpperCase());
        System.out.println();

        if (r.primary != null) {
            System.out.println("  ◆ PRIMARY PICK");
            System.out.println("    " + r.primary.name + "  by " + r.primary.provider +
                               "  [" + r.primary.country + "]" +
                               (r.primary.openSource ? "  [OPEN SOURCE]" : ""));
            System.out.println("    " + r.primary.tagline);
            if (r.primary.axes != null) {
                System.out.printf("    Scores → Capability: %d  Speed: %d  Cost: %d  Ecosystem: %d%n",
                        r.primary.axes.capability, r.primary.axes.speed,
                        r.primary.axes.costEfficiency, r.primary.axes.ecosystemFit);
            }
            if (r.primary.idealFor != null) {
                r.primary.idealFor.forEach(i -> System.out.println("    ▸ " + i));
            }
            System.out.println("    ⚠ " + r.primary.watchOut);
        }

        System.out.println();

        if (r.runnerUp != null) {
            System.out.println("  ○ RUNNER-UP: " + r.runnerUp.name +
                               " (" + r.runnerUp.provider + ", " + r.runnerUp.country + ")");
            System.out.println("    Choose when: " + r.runnerUp.whenToChoose);
        }

        if (r.budgetPick != null) {
            System.out.println("  $ BUDGET PICK: " + r.budgetPick.name +
                               " (" + r.budgetPick.provider + ", " + r.budgetPick.country + ")" +
                               (r.budgetPick.openSource ? " [OPEN SOURCE]" : ""));
            System.out.println("    " + r.budgetPick.costNote);
        }

        if (r.darkHorse != null && r.darkHorse.name != null) {
            System.out.println("  🐎 DARK HORSE: " + r.darkHorse.name +
                               " (" + r.darkHorse.provider + ")");
            System.out.println("    " + r.darkHorse.whyInteresting);
        }

        System.out.println();
        System.out.println("  DECISION FRAMEWORK:");
        System.out.println("  " + r.decisionFramework);
        System.out.println();
        System.out.println("  COST ESTIMATE: " + r.costEstimate);
        System.out.println("  NOTE: " + r.dataDisclaimer);
    }

    private String banner() {
        return """
               ═══════════════════════════════════════════════════════════════════
                 AI MODEL ADVISOR — 4-Agent Pipeline
                 IntakeAgent → SearchAgent (Google) → AnalysisAgent → RecommendationAgent
               ═══════════════════════════════════════════════════════════════════
               """;
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        AdvisorPipeline pipeline = new AdvisorPipeline();

        String useCase;

        if (args.length > 0) {
            // Accept use case as command-line argument
            useCase = String.join(" ", args);
        } else {
            // Interactive prompt
            System.out.print("Describe your AI use case: ");
            Scanner scanner = new Scanner(System.in);
            useCase = scanner.nextLine().trim();
            if (useCase.isBlank()) {
                useCase = "RAG chatbot over internal docs, 500 queries/day, under 2s latency, startup budget";
                System.out.println("Using default: " + useCase);
            }
        }

        pipeline.run(useCase);
    }
}
