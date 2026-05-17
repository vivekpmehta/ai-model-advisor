package com.devcamp.advisor.controller;

import com.devcamp.advisor.agent.SearchAgent;
import com.devcamp.advisor.model.AgentModels.SearchFindings;
import com.devcamp.advisor.model.AgentModels.UseCaseRequirements;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "agent.type", havingValue = "search", matchIfMissing = true)
public class SearchController {
    private final SearchAgent agent;

    public SearchController(SearchAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/process")
    public SearchFindings process(@RequestBody UseCaseRequirements requirements) {
        return agent.process(requirements);
    }
}
