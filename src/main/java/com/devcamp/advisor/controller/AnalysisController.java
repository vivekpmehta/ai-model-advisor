package com.devcamp.advisor.controller;

import com.devcamp.advisor.agent.AnalysisAgent;
import com.devcamp.advisor.model.AgentModels.ScoredAnalysis;
import com.devcamp.advisor.model.AgentRequests.AnalysisRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "agent.type", havingValue = "analysis", matchIfMissing = true)
public class AnalysisController {
    private final AnalysisAgent agent;

    public AnalysisController(AnalysisAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/process")
    public ScoredAnalysis process(@RequestBody AnalysisRequest request) {
        return agent.process(request.requirements, request.findings);
    }
}
