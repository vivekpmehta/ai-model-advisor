package com.devcamp.advisor.controller;

import com.devcamp.advisor.model.AgentModels.Recommendation;
import com.devcamp.advisor.pipeline.AdvisorPipeline;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PipelineController {
    private final AdvisorPipeline pipeline;

    public PipelineController() {
        this.pipeline = new AdvisorPipeline();
    }

    @GetMapping("/run")
    public Recommendation run(@RequestParam String useCase) {
        return pipeline.run(useCase);
    }
}
