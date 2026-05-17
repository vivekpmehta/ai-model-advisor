package com.devcamp.advisor;

import com.devcamp.advisor.agent.AnalysisAgent;
import com.devcamp.advisor.agent.IntakeAgent;
import com.devcamp.advisor.agent.RecommendationAgent;
import com.devcamp.advisor.agent.SearchAgent;
import com.devcamp.advisor.util.DefaultModel;
import com.devcamp.advisor.util.GoogleSearchClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AdvisorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdvisorApplication.class, args);
    }

    @Bean
    public DefaultModel defaultModel() {
        return new DefaultModel();
    }

    @Bean
    public GoogleSearchClient googleSearchClient() {
        return new GoogleSearchClient();
    }

    @Bean
    public IntakeAgent intakeAgent(DefaultModel model) {
        return new IntakeAgent(model);
    }

    @Bean
    public SearchAgent searchAgent(DefaultModel model, GoogleSearchClient searchClient) {
        return new SearchAgent(model, searchClient);
    }

    @Bean
    public AnalysisAgent analysisAgent(DefaultModel model) {
        return new AnalysisAgent(model);
    }

    @Bean
    public RecommendationAgent recommendationAgent(DefaultModel model) {
        return new RecommendationAgent(model);
    }
}
