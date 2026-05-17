package com.devcamp.advisor.controller;

import com.devcamp.advisor.agent.IntakeAgent;
import com.devcamp.advisor.model.AgentModels.UseCaseRequirements;
import com.devcamp.advisor.model.AgentRequests.IntakeRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/intake")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "agent.type", havingValue = "intake", matchIfMissing = true)
public class IntakeController {
    private final IntakeAgent agent;

    public IntakeController(IntakeAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/process")
    public UseCaseRequirements process(@RequestBody IntakeRequest request) {
        return agent.process(request.useCase);
    }
}
