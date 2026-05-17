package com.devcamp.advisor.model;

import java.util.List;

/**
 * Typed data contracts passed between agents.
 * Think of these as the DTOs in your service layer —
 * each agent has a typed input and output, no stringly-typed maps.
 */
public class AgentModels {

    // ── Agent 1 output: structured requirements ──────────────────────────────

    public static class UseCaseRequirements {
        public String useCaseSummary;
        public String primaryTask;          // coding, reasoning, rag, vision, etc.
        public String contextWindowNeeded;  // small_8k, medium_32k, large_128k, huge_200k_plus
        public String latencySensitivity;   // real_time, interactive, batch
        public String costSensitivity;      // cost_critical, balanced, performance_first
        public boolean multimodalNeeded;
        public boolean toolUseNeeded;
        public boolean openSourcePreferred;
        public boolean selfHostNeeded;
        public String volume;               // prototype, low, medium, high, very_high
        public String deploymentTarget;     // cloud_api, self_hosted, edge, unknown
        public List<String> searchQueries;  // queries for the Search Agent
        public List<String> keyRequirements;

        @Override
        public String toString() {
            return "UseCaseRequirements{task=" + primaryTask +
                   ", latency=" + latencySensitivity +
                   ", cost=" + costSensitivity +
                   ", queries=" + searchQueries + "}";
        }
    }

    // ── Agent 2 output: live search findings ─────────────────────────────────

    public static class SearchFindings {
        public String searchSummary;
        public List<ModelCandidate> modelsFound;
        public String notableFindings;
        public String dataFreshness;

        public static class ModelCandidate {
            public String name;
            public String provider;
            public String country;
            public boolean openSource;
            public String contextWindow;
            public String pricingTier;       // free, very_low, low, medium, high, very_high, self_hosted
            public String latencyProfile;    // very_fast, fast, medium, slow
            public List<String> strengths;
            public List<String> limitations;
            public String sourceHint;

            @Override
            public String toString() {
                return name + " (" + provider + ", " + country + ")";
            }
        }
    }

    // ── Agent 3 output: scored models ────────────────────────────────────────

    public static class ScoredAnalysis {
        public String scoringRationale;
        public List<ScoredModel> scoredModels;
        public String diversityCheck;
        public List<String> top3Names;

        public static class ScoredModel {
            public String name;
            public String provider;
            public String country;
            public boolean openSource;
            public int capabilityFit;    // 0-100
            public int speedFit;         // 0-100
            public int costFit;          // 0-100
            public int ecosystemFit;     // 0-100
            public int compositeScore;   // 0-100
            public String whyFits;
            public String keyTradeOff;
        }
    }

    // ── Agent 4 output: final recommendation ─────────────────────────────────

    public static class Recommendation {
        public String headline;
        public ModelPick primary;
        public RunnerUp runnerUp;
        public BudgetPick budgetPick;
        public DarkHorse darkHorse;
        public String decisionFramework;
        public String costEstimate;
        public String dataDisclaimer;

        public static class ModelPick {
            public String name;
            public String provider;
            public String country;
            public boolean openSource;
            public String tagline;
            public Axes axes;
            public List<String> idealFor;
            public String watchOut;

            public static class Axes {
                public int capability;
                public int speed;
                public int costEfficiency;
                public int ecosystemFit;
            }
        }

        public static class RunnerUp {
            public String name;
            public String provider;
            public String country;
            public String whenToChoose;
        }

        public static class BudgetPick {
            public String name;
            public String provider;
            public String country;
            public boolean openSource;
            public String costNote;
        }

        public static class DarkHorse {
            public String name;          // nullable
            public String provider;
            public String whyInteresting;
        }
    }
}
