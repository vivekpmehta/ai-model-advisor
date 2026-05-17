package com.devcamp.advisor.controller;

import com.devcamp.advisor.agent.RecommendationAgent;
import com.devcamp.advisor.model.AgentModels.Recommendation;
import com.devcamp.advisor.model.AgentRequests.RecommendationRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recommendation")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "agent.type", havingValue = "recommendation", matchIfMissing = true)
public class RecommendationController {
    private final RecommendationAgent agent;

    public RecommendationController(RecommendationAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/process")
    public Recommendation process(@RequestBody RecommendationRequest request) {
        return agent.process(request.requirements, request.findings, request.analysis);
    }
}
