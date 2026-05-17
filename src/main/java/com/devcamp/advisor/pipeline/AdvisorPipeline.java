package com.devcamp.advisor.pipeline;

import com.devcamp.advisor.agent.AnalysisAgent;
import com.devcamp.advisor.agent.IntakeAgent;
import com.devcamp.advisor.agent.RecommendationAgent;
import com.devcamp.advisor.agent.SearchAgent;
import com.devcamp.advisor.model.AgentModels.*;
import com.devcamp.advisor.model.AgentRequests.*;
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
 *   export GOOGLE_PROJECT_ID=my-project
 *   export VERTEX_DATA_STORE_ID=my-ds-123
 *   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json
 *   ./gradlew run
 */
public class AdvisorPipeline {

    private static final Logger log = LoggerFactory.getLogger(AdvisorPipeline.class);

    // ── Shared infrastructure ────────────────────────────────────────────────
    private final DefaultModel       defaultModel;
    private final okhttp3.OkHttpClient httpClient;

    // ── Agent URLs ──────────────────────────────────────────────────────────
    private final String intakeUrl;
    private final String searchUrl;
    private final String analysisUrl;
    private final String recommendationUrl;

    public AdvisorPipeline() {
        this.defaultModel = new DefaultModel();
        this.httpClient = new okhttp3.OkHttpClient();

        // Load URLs from env vars or use localhost defaults for local dev
        this.intakeUrl         = getEnv("INTAKE_AGENT_URL",         "http://localhost:8080/intake/process");
        this.searchUrl         = getEnv("SEARCH_AGENT_URL",         "http://localhost:8080/search/process");
        this.analysisUrl       = getEnv("ANALYSIS_AGENT_URL",       "http://localhost:8080/analysis/process");
        this.recommendationUrl = getEnv("RECOMMENDATION_AGENT_URL", "http://localhost:8080/recommendation/process");
    }

    private String getEnv(String name, String defaultValue) {
        String val = System.getenv(name);
        return (val != null && !val.isBlank()) ? val : defaultValue;
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
        IntakeRequest intakeReq = new IntakeRequest();
        intakeReq.useCase = useCase;
        UseCaseRequirements requirements = post(intakeUrl, intakeReq, UseCaseRequirements.class);
        System.out.println("  Task:     " + requirements.primaryTask);
        System.out.println("  Latency:  " + requirements.latencySensitivity);
        System.out.println("  Cost:     " + requirements.costSensitivity);
        System.out.println("  Queries:  " + requirements.searchQueries);

        // ── AGENT 2: Search ──────────────────────────────────────────────────
        System.out.println("\n[2/4] SEARCH AGENT — querying Vertex AI Search...");
        SearchFindings findings = post(searchUrl, requirements, SearchFindings.class);
        System.out.println("  Models found: " +
                (findings.modelsFound != null ? findings.modelsFound.size() : 0));
        if (findings.modelsFound != null) {
            findings.modelsFound.forEach(m ->
                    System.out.println("    • " + m.name + " (" + m.provider + ", " + m.country + ")" +
                                       (m.openSource ? " [open-source]" : "")));
        }

        // ── AGENT 3: Analysis ────────────────────────────────────────────────
        System.out.println("\n[3/4] ANALYSIS AGENT — scoring models against requirements...");
        AnalysisRequest analysisReq = new AnalysisRequest();
        analysisReq.requirements = requirements;
        analysisReq.findings = findings;
        ScoredAnalysis analysis = post(analysisUrl, analysisReq, ScoredAnalysis.class);
        System.out.println("  Top 3: " + analysis.top3Names);
        System.out.println("  " + analysis.diversityCheck);

        // ── AGENT 4: Recommendation ──────────────────────────────────────────
        System.out.println("\n[4/4] RECOMMENDATION AGENT — synthesising final recommendation...");
        RecommendationRequest recReq = new RecommendationRequest();
        recReq.requirements = requirements;
        recReq.findings = findings;
        recReq.analysis = analysis;
        Recommendation recommendation = post(recommendationUrl, recReq, Recommendation.class);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("\n" + "═".repeat(70));
        renderRecommendation(recommendation);
        System.out.printf("\n  Pipeline completed in %.1fs%n", elapsed / 1000.0);
        System.out.println("═".repeat(70));

        return recommendation;
    }

    private <T> T post(String url, Object body, Class<T> responseClass) {
        String json = defaultModel.toJson(body);
        okhttp3.RequestBody reqBody = okhttp3.RequestBody.create(
                json, okhttp3.MediaType.get("application/json; charset=utf-8"));
        
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .post(reqBody)
                .build();

        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to call agent at " + url + ": " + response);
            }
            return defaultModel.fromJson(response.body().string(), responseClass);
        } catch (Exception e) {
            throw new RuntimeException("Error calling agent at " + url, e);
        }
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

        String useCase = "";

        if (args.length > 0) {
            // Accept use case as command-line argument
            useCase = String.join(" ", args);
        } else {
            // Interactive prompt
            System.out.print("Describe your AI use case: ");
            Scanner scanner = new Scanner(System.in);
            if (scanner.hasNextLine()) {
                useCase = scanner.nextLine().trim();
            }
            if (useCase.isBlank()) {
                useCase = "RAG chatbot over internal docs, 500 queries/day, under 2s latency, startup budget";
                System.out.println("Using default: " + useCase);
            }
        }

        pipeline.run(useCase);
    }
}
