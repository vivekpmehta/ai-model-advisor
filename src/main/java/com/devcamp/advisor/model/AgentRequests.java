package com.devcamp.advisor.model;

import com.devcamp.advisor.model.AgentModels.*;

public class AgentRequests {

    public static class IntakeRequest {
        public String useCase;
    }

    public static class AnalysisRequest {
        public UseCaseRequirements requirements;
        public SearchFindings findings;
    }

    public static class RecommendationRequest {
        public UseCaseRequirements requirements;
        public SearchFindings findings;
        public ScoredAnalysis analysis;
    }
}
